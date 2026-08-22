package com.riyaz.rsscloudsync

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import com.riyaz.rsscloudsync.databinding.ActivityContactBinding

class ContactActivity : AppCompatActivity() {
    private lateinit var binding: ActivityContactBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityContactBinding.inflate(layoutInflater)
        setContentView(binding.root)
        setSupportActionBar(binding.toolbar)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        supportActionBar?.title = "Contact"

        binding.emailSupportCard.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:rsscctvsolution@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "RSS CLOUD SYNC Support")
            }
            startActivity(Intent.createChooser(intent, "Contact support"))
        }

        binding.reportProblemCard.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:rsscctvsolution@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "RSS CLOUD SYNC Problem Report")
            }
            startActivity(Intent.createChooser(intent, "Report a problem"))
        }

        binding.featureRequestCard.setOnClickListener {
            val intent = Intent(Intent.ACTION_SENDTO).apply {
                data = Uri.parse("mailto:rsscctvsolution@gmail.com")
                putExtra(Intent.EXTRA_SUBJECT, "RSS CLOUD SYNC Feature Request")
            }
            startActivity(Intent.createChooser(intent, "Send feature request"))
        }
    }

    override fun onSupportNavigateUp(): Boolean {
        finish()
        return true
    }
}