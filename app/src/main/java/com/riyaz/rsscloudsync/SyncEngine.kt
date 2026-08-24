package com.riyaz.rsscloudsync

import android.content.ContentResolver
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import java.io.BufferedInputStream
import java.io.BufferedOutputStream
import java.util.ArrayDeque

class SyncEngine(private val resolver: ContentResolver, private val context: Context) {
    enum class Direction { TWO_WAY, UPLOAD_ONLY, UPLOAD_MIRROR, UPLOAD_THEN_DELETE, DOWNLOAD_ONLY, DOWNLOAD_MIRROR, DOWNLOAD_THEN_DELETE }
    data class Progress(val filesProcessed:Int,val totalFiles:Int,val filesChanged:Int,val uploadedFiles:Int,val downloadedFiles:Int,val failedFiles:Int,val videoFiles:Int,val audioFiles:Int,val documentFiles:Int,val otherFiles:Int,val bytesTransferred:Long,val currentPath:String)
    data class Result(val filesProcessed:Int,val filesChanged:Int,val uploadedFiles:Int,val downloadedFiles:Int,val failedFiles:Int,val videoFiles:Int,val audioFiles:Int,val documentFiles:Int,val otherFiles:Int,val bytesTransferred:Long,val cancelled:Boolean,val error:String?=null)
    @Volatile private var cancelled=false
    fun cancel(){cancelled=true}

    fun sync(sourceTree:Uri,targetTree:Uri,direction:Direction,listener:((Progress)->Unit)?=null):Result{
        cancelled=false; val started=System.currentTimeMillis(); val stats=Stats()
        return try{
            val source=index(sourceTree); val target=index(targetTree)
            if(cancelled)return finish(started,direction,result(stats,true,"Sync cancelled"))
            when(direction){
                Direction.TWO_WAY->twoWay(sourceTree,targetTree,source,target,stats,listener)
                Direction.UPLOAD_ONLY->oneWay(sourceTree,targetTree,source,target,false,listener,false,true,stats)
                Direction.UPLOAD_MIRROR->oneWay(sourceTree,targetTree,source,target,true,listener,false,true,stats)
                Direction.UPLOAD_THEN_DELETE->oneWay(sourceTree,targetTree,source,target,false,listener,true,true,stats)
                Direction.DOWNLOAD_ONLY->oneWay(targetTree,sourceTree,target,source,false,listener,false,false,stats)
                Direction.DOWNLOAD_MIRROR->oneWay(targetTree,sourceTree,target,source,true,listener,false,false,stats)
                Direction.DOWNLOAD_THEN_DELETE->oneWay(targetTree,sourceTree,target,source,false,listener,true,false,stats)
            }
            val message=when{cancelled->"Sync cancelled";stats.failed>0->"Completed with ${stats.failed} file error${if(stats.failed==1)"" else"s"}";else->null}
            finish(started,direction,result(stats,cancelled,message))
        }catch(e:SyncCancelledException){finish(started,direction,result(stats,true,"Sync cancelled"))}
        catch(e:Exception){val message=e.message?:"Sync failed";finish(started,direction,result(stats,cancelled,if(stats.bytes>0L)"Completed with warning: $message" else message))}
    }

    private fun finish(started:Long,direction:Direction,result:Result):Result{
        SyncHistoryManager.add(context,SyncHistoryManager.Entry(System.currentTimeMillis(),direction.name,result.filesProcessed,result.filesChanged,result.uploadedFiles,result.downloadedFiles,result.failedFiles,result.videoFiles,result.audioFiles,result.documentFiles,result.otherFiles,result.bytesTransferred,System.currentTimeMillis()-started,result.error==null&&!result.cancelled&&result.failedFiles==0,result.error?:if(result.cancelled)"Cancelled" else if(result.failedFiles>0)"Completed with warnings" else"Sync completed"))
        return result
    }
    private data class Stats(var processed:Int=0,var changed:Int=0,var uploaded:Int=0,var downloaded:Int=0,var failed:Int=0,var video:Int=0,var audio:Int=0,var documents:Int=0,var other:Int=0,var bytes:Long=0){
        fun addCategory(name:String){when(name.substringAfterLast('.',"").lowercase()){"mp4","mkv","mov","avi","webm","3gp","m4v","flv","wmv","mpeg","mpg"->video++;"mp3","wav","m4a","aac","flac","ogg","opus","wma","amr"->audio++;"pdf","doc","docx","xls","xlsx","ppt","pptx","txt","csv","rtf","odt","ods","odp","epub"->documents++;else->other++}}
    }
    private fun oneWay(sourceTree:Uri,targetTree:Uri,source:Map<String,Item>,target:Map<String,Item>,mirror:Boolean,listener:((Progress)->Unit)?,deleteSource:Boolean,uploading:Boolean,stats:Stats){
        val files=source.filterValues{!it.directory}; for((path,item)in files){if(cancelled)return;try{val existing=target[path];if(existing==null||item.size!=existing.size||item.modified>existing.modified){stats.bytes+=copyFile(sourceTree,targetTree,item,path);stats.changed++;if(uploading)stats.uploaded++else stats.downloaded++;stats.addCategory(item.name);if(deleteSource)delete(sourceTree,item.id)}}catch(e:Exception){if(e is SyncCancelledException)throw e;stats.failed++};stats.processed++;listener?.invoke(progress(stats,files.size,path))}
        if(mirror)for((_,item)in target)if(!item.directory&&!source.containsKey(item.path)){if(cancelled)return;try{delete(targetTree,item.id);stats.changed++}catch(_:Exception){stats.failed++}}
    }
    private fun twoWay(sourceTree:Uri,targetTree:Uri,source:Map<String,Item>,target:Map<String,Item>,stats:Stats,listener:((Progress)->Unit)?){val paths=LinkedHashSet<String>().apply{addAll(source.keys);addAll(target.keys)};val files=paths.filter{!(source[it]?.directory==true||target[it]?.directory==true)};for(path in files){if(cancelled)return;try{val a=source[path];val b=target[path];when{a!=null&&b==null->{stats.bytes+=copyFile(sourceTree,targetTree,a,path);stats.changed++;stats.uploaded++;stats.addCategory(a.name)};b!=null&&a==null->{stats.bytes+=copyFile(targetTree,sourceTree,b,path);stats.changed++;stats.downloaded++;stats.addCategory(b.name)};a!=null&&b!=null&&(a.size!=b.size||a.modified!=b.modified)->{if(a.modified>=b.modified){stats.bytes+=copyFile(sourceTree,targetTree,a,path);stats.uploaded++;stats.addCategory(a.name)}else{stats.bytes+=copyFile(targetTree,sourceTree,b,path);stats.downloaded++;stats.addCategory(b.name)};stats.changed++}}}catch(e:Exception){if(e is SyncCancelledException)throw e;stats.failed++};stats.processed++;listener?.invoke(progress(stats,files.size,path))}}
    private fun progress(s:Stats,total:Int,path:String)=Progress(s.processed,total,s.changed,s.uploaded,s.downloaded,s.failed,s.video,s.audio,s.documents,s.other,s.bytes,path)
    private fun result(s:Stats,cancelled:Boolean,error:String?=null)=Result(s.processed,s.changed,s.uploaded,s.downloaded,s.failed,s.video,s.audio,s.documents,s.other,s.bytes,cancelled,error)
    private data class Item(val id:String,val path:String,val name:String,val mimeType:String,val size:Long,val modified:Long,val directory:Boolean)
    private fun index(tree:Uri):Map<String,Item>{val result=LinkedHashMap<String,Item>();val q=ArrayDeque<Pair<String,String>>();q.add("" to DocumentsContract.getTreeDocumentId(tree));while(q.isNotEmpty()){if(cancelled)break;val(parent,id)=q.removeFirst();val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,id);resolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null)?.use{c->val idCol=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);val nameCol=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);val mimeCol=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_MIME_TYPE);val sizeCol=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_SIZE);val modCol=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_LAST_MODIFIED);while(c.moveToNext()){val childId=c.getString(idCol);val name=c.getString(nameCol)? :"Unnamed";val path=if(parent.isEmpty())name else"$parent/$name";val mime=c.getString(mimeCol)? :"application/octet-stream";val dir=mime==DocumentsContract.Document.MIME_TYPE_DIR;result[path]=Item(childId,path,name,mime,if(c.isNull(sizeCol))0 else c.getLong(sizeCol),if(c.isNull(modCol))0 else c.getLong(modCol),dir);if(dir)q.add(path to childId)}}};return result}
    private fun copyFile(sourceTree:Uri,targetTree:Uri,item:Item,path:String):Long{val sourceUri=DocumentsContract.buildDocumentUriUsingTree(sourceTree,item.id);val parentId=ensureFolderPath(targetTree,path.substringBeforeLast('/',''));val parentUri=DocumentsContract.buildDocumentUriUsingTree(targetTree,parentId);val existing=findChild(targetTree,parentId,item.name);val targetUri=if(existing!=null)DocumentsContract.buildDocumentUriUsingTree(targetTree,existing)else DocumentsContract.createDocument(resolver,parentUri,item.mimeType,item.name)?:throw IllegalStateException("Unable to create target file: $path");resolver.openInputStream(sourceUri).use{input->resolver.openOutputStream(targetUri,"wt").use{output->if(input==null||output==null)throw IllegalStateException("Unable to open file: $path");BufferedInputStream(input).use{ins->BufferedOutputStream(output).use{outs->val buffer=ByteArray(64*1024);var total=0L;while(!cancelled){val n=ins.read(buffer);if(n<0)break;outs.write(buffer,0,n);total+=n};outs.flush();if(cancelled)throw SyncCancelledException();return total}}}}
    private fun ensureFolderPath(tree:Uri,path:String):String{var current=DocumentsContract.getTreeDocumentId(tree);if(path.isEmpty())return current;for(part in path.split('/').filter{it.isNotEmpty()})current=findChild(tree,current,part)?:run{val parent=DocumentsContract.buildDocumentUriUsingTree(tree,current);val created=DocumentsContract.createDocument(resolver,parent,DocumentsContract.Document.MIME_TYPE_DIR,part)?:throw IllegalStateException("Unable to create target folder: $part");DocumentsContract.getDocumentId(created)};return current}
    private fun findChild(tree:Uri,parentId:String,name:String):String?{val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId);resolver.query(children,arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use{c->val id=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DOCUMENT_ID);val n=c.getColumnIndexOrThrow(DocumentsContract.Document.COLUMN_DISPLAY_NAME);while(c.moveToNext())if(c.getString(n)==name)return c.getString(id)};return null}
    private fun delete(tree:Uri,id:String){if(!DocumentsContract.deleteDocument(resolver,DocumentsContract.buildDocumentUriUsingTree(tree,id)))throw IllegalStateException("Unable to delete document")}
    private class SyncCancelledException:Exception("Sync cancelled")
}
