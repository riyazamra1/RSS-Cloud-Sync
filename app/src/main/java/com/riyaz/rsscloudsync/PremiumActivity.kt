package com.riyaz.rsscloudsync

import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rsscloudsync.databinding.ActivityFeatureListBinding

class PremiumActivity : AppCompatActivity() {
    private lateinit var binding: ActivityFeatureListBinding
    private data class Feature(val icon:String,val name:String,val free:Boolean,val premium:Boolean)
    private val features=listOf(
        Feature("↔","Two-way Sync",true,true),Feature("▶","Manual sync",true,true),Feature("▣","1 synced folder pair",true,true),Feature("☁","Google Drive",true,true),Feature("◷","Basic sync history",true,true),Feature("◐","Light / Dark / System",true,true),Feature("↑","Upload only",false,true),Feature("↥","Upload mirror",false,true),Feature("⌫","Upload then delete",false,true),Feature("↓","Download only",false,true),Feature("↧","Download mirror",false,true),Feature("⌫","Download then delete",false,true),Feature("⟳","Automatic sync",false,true),Feature("⚡","Instant upload",false,true),Feature("◷","Advanced scheduling",false,true),Feature("≡","Advanced file filtering",false,true),Feature("★","Priority sync",false,true),Feature("☷","Extended sync history",false,true),Feature("▤","Multiple folder pairs",false,true),Feature("✓","No ads",false,true),Feature("A","Ads",true,false)
    )
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);binding=ActivityFeatureListBinding.inflate(layoutInflater);setContentView(binding.root);setSupportActionBar(binding.toolbar);supportActionBar?.setDisplayHomeAsUpEnabled(true);supportActionBar?.title="Plans";applyTheme();buildComparison(binding.comparisonTable)}
    private fun buildComparison(table:TableLayout){table.removeAllViews();val dark=(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;val primary=if(dark)Color.WHITE else Color.rgb(25,32,44);val secondary=if(dark)Color.rgb(145,160,182)else Color.rgb(100,112,130);val divider=if(dark)Color.rgb(38,53,75)else Color.rgb(229,234,241);val freeAccent=Color.rgb(110,168,255);val premiumAccent=Color.rgb(72,215,255)
        features.forEachIndexed{i,f->val row=TableRow(this).apply{gravity=Gravity.CENTER_VERTICAL;setPadding(dp(10),dp(2),dp(4),dp(2));setBackgroundColor(if(i%2==0)Color.rgb(250,252,255)else Color.WHITE)};val name=TextView(this).apply{text="${f.icon}  ${f.name}";textSize=14f;setTextColor(primary);gravity=Gravity.CENTER_VERTICAL;maxLines=1;setTypeface(typeface,Typeface.BOLD);includeFontPadding=false;layoutParams=TableRow.LayoutParams(0,dp(44),1f)};row.addView(name);row.addView(statusCell(f.free,freeAccent,secondary,42));row.addView(statusCell(f.premium,premiumAccent,secondary,48));table.addView(row);if(i!=features.lastIndex)table.addView(TextView(this).apply{setBackgroundColor(divider);layoutParams=TableLayout.LayoutParams(-1,dp(1))})}}
    private fun statusCell(enabled:Boolean,accent:Int,secondary:Int,width:Int)=TextView(this).apply{text=if(enabled)"✓" else "—";textSize=if(enabled)19f else 16f;gravity=Gravity.CENTER;includeFontPadding=false;setTypeface(typeface,Typeface.BOLD);setTextColor(if(enabled)accent else secondary);layoutParams=TableRow.LayoutParams(dp(width),dp(44))}
    private fun applyTheme(){val dark=(resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK)==Configuration.UI_MODE_NIGHT_YES;binding.root.setBackgroundColor(if(dark)Color.rgb(7,11,20)else Color.rgb(244,247,251));binding.pageTitle.setTextColor(if(dark)Color.WHITE else Color.rgb(17,24,39));binding.pageSubtitle.setTextColor(if(dark)Color.rgb(155,169,190)else Color.rgb(102,112,133));binding.toolbar.setTitleTextColor(if(dark)Color.WHITE else Color.rgb(17,24,39))}
    private fun dp(v:Int)= (v*resources.displayMetrics.density).toInt()
    override fun onSupportNavigateUp():Boolean{finish();return true}
}
