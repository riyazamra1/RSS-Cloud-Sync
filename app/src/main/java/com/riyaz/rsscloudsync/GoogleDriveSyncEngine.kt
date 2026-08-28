package com.riyaz.rsscloudsync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.util.ArrayDeque

/** Google Drive synchronization engine. Local SAF indexing is delegated to SafFileScanner. */
class GoogleDriveSyncEngine(private val context: Context, private val resolver: ContentResolver) {
    enum class Direction { UPLOAD_ONLY, UPLOAD_MIRROR, UPLOAD_THEN_DELETE, DOWNLOAD_ONLY, DOWNLOAD_MIRROR, DOWNLOAD_THEN_DELETE, TWO_WAY }
    data class Options(val excludeHiddenFiles: Boolean = true, val excludeSubfolders: Boolean = false, val deleteEmptySubfolders: Boolean = false)
    data class Progress(val processed:Int,val total:Int,val uploaded:Int,val downloaded:Int,val changed:Int,val failed:Int,val bytes:Long,val currentPath:String)
    data class Result(val processed:Int,val uploaded:Int,val downloaded:Int,val changed:Int,val failed:Int,val bytes:Long,val cancelled:Boolean=false)
    private data class LocalItem(val uri:Uri,val path:String,val name:String,val mime:String,val size:Long,val modified:Long)
    private data class RemoteItem(val entry:DriveClient.Entry,val path:String,val directory:Boolean)
    @Volatile private var cancelled=false
    fun cancel(){ cancelled=true }
    fun isCancelled()=cancelled

    fun sync(localTree:Uri,driveFolderId:String,direction:Direction,options:Options=Options(),listener:((Progress)->Unit)?=null):Result {
        cancelled=false
        val drive=DriveClient(context)
        val local=indexLocal(localTree,options)
        if(cancelled)return Result(0,0,0,0,0,0,cancelled=true)
        val remote=indexDrive(drive,driveFolderId)
        if(cancelled)return Result(0,0,0,0,0,0,cancelled=true)
        val paths=LinkedHashSet<String>().apply{addAll(local.keys);addAll(remote.keys.filter{!remote.getValue(it).directory})}
        val files=paths.filter{local[it]!=null||remote[it]?.directory!=true}
        val folderCache=HashMap<String,String>().apply{put("",driveFolderId)}
        var processed=0;var uploaded=0;var downloaded=0;var changed=0;var failed=0;var bytes=0L
        fun report(path:String){processed++;listener?.invoke(Progress(processed,files.size,uploaded,downloaded,changed,failed,bytes,path))}
        for(path in files){
            if(cancelled)break
            try{
                val l=local[path];val r=remote[path]
                when(direction){
                    Direction.UPLOAD_ONLY->{if(l!=null){bytes+=drive.upload(l.uri,remoteFolderForPath(drive,driveFolderId,path,folderCache),l.name,l.mime,r?.entry?.id);uploaded++;changed++}}
                    Direction.UPLOAD_MIRROR->{if(l!=null&&(r==null||isLocalNewer(l,r))){bytes+=drive.upload(l.uri,remoteFolderForPath(drive,driveFolderId,path,folderCache),l.name,l.mime,r?.entry?.id);uploaded++;changed++}}
                    Direction.UPLOAD_THEN_DELETE->{if(l!=null){bytes+=drive.upload(l.uri,remoteFolderForPath(drive,driveFolderId,path,folderCache),l.name,l.mime,r?.entry?.id);uploaded++;changed++;deleteLocal(l.uri)}}
                    Direction.DOWNLOAD_ONLY->{if(r!=null){bytes+=downloadToLocal(drive,localTree,r,path);downloaded++;changed++}}
                    Direction.DOWNLOAD_MIRROR->{if(r!=null&&(l==null||isRemoteNewer(l,r))){bytes+=downloadToLocal(drive,localTree,r,path);downloaded++;changed++}}
                    Direction.DOWNLOAD_THEN_DELETE->{if(r!=null){bytes+=downloadToLocal(drive,localTree,r,path);downloaded++;changed++;drive.delete(r.entry.id)}}
                    Direction.TWO_WAY->{when{l!=null&&r==null->{bytes+=drive.upload(l.uri,remoteFolderForPath(drive,driveFolderId,path,folderCache),l.name,l.mime);uploaded++;changed++};l==null&&r!=null->{bytes+=downloadToLocal(drive,localTree,r,path);downloaded++;changed++};l!=null&&r!=null&&isDifferent(l,r)->{if(isLocalNewer(l,r)){bytes+=drive.upload(l.uri,remoteFolderForPath(drive,driveFolderId,path,folderCache),l.name,l.mime,r.entry.id);uploaded++}else{bytes+=downloadToLocal(drive,localTree,r,path);downloaded++};changed++}}}
                }
            }catch(_:Exception){failed++}
            report(path)
        }
        if(!cancelled&&direction==Direction.UPLOAD_MIRROR)for((_,item)in remote)if(!item.directory&&!local.containsKey(item.path))try{drive.delete(item.entry.id);changed++}catch(_:Exception){failed++}
        if(!cancelled&&direction==Direction.DOWNLOAD_MIRROR)for((_,item)in local)if(!remote.containsKey(item.path))try{deleteLocal(item.uri);changed++}catch(_:Exception){failed++}
        if(!cancelled&&options.deleteEmptySubfolders)deleteEmptyLocalFolders(localTree)
        return Result(processed,uploaded,downloaded,changed,failed,bytes,cancelled)
    }

    fun uploadSelectedFiles(files:List<Uri>,driveFolderId:String,listener:((Progress)->Unit)?=null):Result{
        cancelled=false;val drive=DriveClient(context);var processed=0;var uploaded=0;var failed=0;var changed=0;var bytes=0L
        for(uri in files){if(cancelled)break;val name=queryName(uri);try{val mime=resolver.getType(uri)?:"application/octet-stream";val existing=drive.findChild(driveFolderId,name);bytes+=drive.upload(uri,driveFolderId,name,mime,existing?.id);uploaded++;changed++}catch(_:Exception){failed++};processed++;listener?.invoke(Progress(processed,files.size,uploaded,0,changed,failed,bytes,name))}
        return Result(processed,uploaded,0,changed,failed,bytes,cancelled)
    }

    private fun queryName(uri:Uri):String=resolver.query(uri,arrayOf(android.provider.OpenableColumns.DISPLAY_NAME),null,null,null)?.use{if(it.moveToFirst())it.getString(0) else "file"}?:"file"

    /** Uses the optimized SAF scanner once per sync run instead of maintaining a second traversal implementation here. */
    private fun indexLocal(tree:Uri,options:Options):Map<String,LocalItem>{
        val scanner=SafFileScanner(resolver)
        val result=scanner.scan(tree,SafFileScanner.Options(options.excludeHiddenFiles,options.excludeSubfolders)){_,_->if(cancelled)scanner.cancel()}
        if(result.cancelled||cancelled){cancelled=true;return emptyMap()}
        return result.files.mapValues{(_,item)->LocalItem(item.uri,item.path,item.name,item.mimeType,item.size,item.modified)}
    }

    private fun indexDrive(drive:DriveClient,rootId:String):Map<String,RemoteItem>{
        val result=LinkedHashMap<String,RemoteItem>();val queue=ArrayDeque<Pair<String,String>>();queue.add("" to rootId)
        while(queue.isNotEmpty()&&!cancelled){val(parentPath,parentId)=queue.removeFirst();for(entry in drive.listChildren(parentId)){if(cancelled)break;val path=if(parentPath.isBlank())entry.name else "$parentPath/${entry.name}";val dir=entry.mimeType==DriveClient.FOLDER_MIME;result[path]=RemoteItem(entry,path,dir);if(dir)queue.add(path to entry.id)}}
        return result
    }

    private fun remoteFolderForPath(drive:DriveClient,rootId:String,path:String,cache:MutableMap<String,String>):String{
        val folderPath=path.substringBeforeLast('/','');if(folderPath.isBlank())return rootId;cache[folderPath]?.let{return it};var parent=rootId;val built=StringBuilder()
        for(part in folderPath.split('/').filter{it.isNotBlank()}){if(built.isNotEmpty())built.append('/');built.append(part);val key=built.toString();parent=cache[key]?:drive.createFolder(parent,part).also{cache[key]=it}};return parent
    }

    private fun downloadToLocal(drive:DriveClient,localTree:Uri,remote:RemoteItem,path:String):Long{
        val parentId=ensureLocalFolder(localTree,path.substringBeforeLast('/',''));val parentUri=DocumentsContract.buildDocumentUriUsingTree(localTree,parentId);val existing=findLocalChild(localTree,parentId,remote.entry.name)
        val target=existing?.let{DocumentsContract.buildDocumentUriUsingTree(localTree,it)}?:DocumentsContract.createDocument(resolver,parentUri,remote.entry.mimeType.ifBlank{"application/octet-stream"},remote.entry.name)?:throw IllegalStateException("Unable to create local file $path")
        resolver.openOutputStream(target,"wt").use{output->if(output==null)throw IllegalStateException("Unable to open local file $path");return drive.download(remote.entry,output)}
    }

    private fun ensureLocalFolder(tree:Uri,path:String):String{var current=DocumentsContract.getTreeDocumentId(tree);for(part in path.split('/').filter{it.isNotBlank()}){current=findLocalChild(tree,current,part)?:run{val created=DocumentsContract.createDocument(resolver,DocumentsContract.buildDocumentUriUsingTree(tree,current),DocumentsContract.Document.MIME_TYPE_DIR,part)?:throw IllegalStateException("Unable to create local folder $part");DocumentsContract.getDocumentId(created)}};return current}
    private fun findLocalChild(tree:Uri,parentId:String,name:String):String?{val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId);resolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use{c->val idCol=c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);val nameCol=c.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME);if(idCol<0||nameCol<0)return null;while(c.moveToNext())if(c.getString(nameCol)==name)return c.getString(idCol)};return null}
    private fun deleteLocal(uri:Uri){if(!DocumentsContract.deleteDocument(resolver,uri))throw IllegalStateException("Unable to delete local file")}
    private fun deleteEmptyLocalFolders(tree:Uri){deleteEmptyChildren(tree,DocumentsContract.getTreeDocumentId(tree))}
    private fun deleteEmptyChildren(tree:Uri,parentId:String):Boolean{var empty=true;val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId);val dirs=ArrayList<String>();resolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_MIME_TYPE),null,null,null)?.use{c->val idCol=c.getColumnIndex(DocumentsContract.Document.COLUMN_DOCUMENT_ID);val mimeCol=c.getColumnIndex(DocumentsContract.Document.COLUMN_MIME_TYPE);if(idCol<0||mimeCol<0)return false;while(c.moveToNext()){val id=c.getString(idCol);if(c.getString(mimeCol)==DocumentsContract.Document.MIME_TYPE_DIR)dirs.add(id)else empty=false}};for(id in dirs)if(deleteEmptyChildren(tree,id))try{DocumentsContract.deleteDocument(resolver,DocumentsContract.buildDocumentUriUsingTree(tree,id))}catch(_:Exception){}else empty=false;return empty}
    private fun isDifferent(local:LocalItem,remote:RemoteItem)=local.size!=remote.entry.size||local.modified!=remote.entry.modified
    private fun isLocalNewer(local:LocalItem,remote:RemoteItem)=when{local.modified>remote.entry.modified->true;local.modified<remote.entry.modified->false;else->local.size!=remote.entry.size}
    private fun isRemoteNewer(local:LocalItem,remote:RemoteItem)=when{remote.entry.modified>local.modified->true;remote.entry.modified<local.modified->false;else->remote.entry.size!=local.size}
}
