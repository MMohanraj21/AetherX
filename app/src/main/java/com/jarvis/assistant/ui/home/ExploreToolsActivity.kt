package com.jarvis.assistant.ui.home

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.jarvis.assistant.databinding.ActivityExploreToolsBinding
import com.jarvis.assistant.databinding.ItemToolActionCardBinding

class ExploreToolsActivity : AppCompatActivity() {

    data class ToolItem(
        val name: String,
        val category: String,
        val description: String,
        val examplePrompt: String,
        val badge: String = "AUTONOMOUS"
    )

    private lateinit var binding: ActivityExploreToolsBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityExploreToolsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnBackExplore.setOnClickListener {
            finish()
        }

        val toolsList = listOf(
            // Apps & Navigation
            ToolItem("open_app", "Apps & Navigation", "Launches any installed application directly by name.", "\"Open YouTube\""),
            ToolItem("open_url", "Apps & Navigation", "Opens specific web URLs in custom Chrome tabs.", "\"Open github.com/hexvora777\""),
            // Web & Search
            ToolItem("search_web", "Web & Search", "Queries Google/Perplexity for real-time web knowledge.", "\"Latest Tech News today\""),
            ToolItem("google_search", "Web & Search", "Fallback browser search engine query.", "\"Search Google for OriginOS 6\""),
            // YouTube & Media
            ToolItem("search_youtube", "YouTube & Media", "Searches and launches YouTube playback directly.", "\"Play AR Rahman Tamil hits\""),
            ToolItem("play_media", "YouTube & Media", "Universal Media Controller play action.", "\"Play music\""),
            ToolItem("pause_media", "YouTube & Media", "Universal Media Controller pause action.", "\"Pause song\""),
            ToolItem("next_track", "YouTube & Media", "Skips to next track in active media session.", "\"Next track\""),
            ToolItem("previous_track", "YouTube & Media", "Skips to previous track in media player.", "\"Previous song\""),
            // WhatsApp & Communication
            ToolItem("send_whatsapp", "WhatsApp & Communication", "Sends direct WhatsApp messages via Accessibility.", "\"WhatsApp Mohanraj I am on my way\""),
            ToolItem("read_notifications", "WhatsApp & Communication", "Reads incoming notification messages aloud.", "\"Read my latest messages\""),
            ToolItem("reply_notification", "WhatsApp & Communication", "Directly replies to incoming notification remote inputs.", "\"Reply to Suresh: Will call in 5 mins\""),
            // Phone & Telecom
            ToolItem("make_call", "Phone & Telecom", "Initiates direct phone calls via TelecomManager.", "\"Call Appa on mobile\""),
            ToolItem("answer_call", "Phone & Telecom", "Answers incoming phone calls hands-free.", "\"Answer the call\""),
            ToolItem("end_call", "Phone & Telecom", "Terminates active cellular phone calls.", "\"Hang up the call\""),
            ToolItem("screen_call", "Phone & Telecom", "AI Call Screening agent for unknown callers.", "\"Screen this caller\""),
            // Device Controls
            ToolItem("toggle_flashlight", "Device Controls", "Toggles camera torch ON/OFF.", "\"Turn on flashlight\""),
            ToolItem("set_volume", "Device Controls", "Sets device media, ring, or alarm volume levels.", "\"Set volume to 80 percent\""),
            ToolItem("set_brightness", "Device Controls", "Adjusts screen brightness level.", "\"Set brightness to maximum\""),
            ToolItem("toggle_wifi", "Device Controls", "Toggles or opens WiFi settings.", "\"Turn on WiFi\""),
            ToolItem("toggle_bluetooth", "Device Controls", "Toggles or opens Bluetooth settings.", "\"Enable Bluetooth\""),
            // Screen Automation
            ToolItem("capture_screen", "Screen Automation", "Captures screen buffer via MediaProjection.", "\"Look at my screen and summarize\""),
            ToolItem("click_coordinate", "Screen Automation", "Performs automated touch click at x, y coordinate.", "\"Tap the confirm button\""),
            ToolItem("scroll_screen", "Screen Automation", "Performs programmatic up/down swipe gestures.", "\"Scroll down\""),
            ToolItem("read_screen_text", "Screen Automation", "OCR text extraction from active screen nodes.", "\"What does the error code say?\""),
            // Alarms & Reminders
            ToolItem("set_alarm", "Alarms & Reminders", "Schedules alarm via Android AlarmClock provider.", "\"Wake me up at 6:30 AM\""),
            ToolItem("set_timer", "Alarms & Reminders", "Starts countdown timer.", "\"Set a 15-minute timer\""),
            ToolItem("set_reminder", "Alarms & Reminders", "Stores persistent reminder in Room DB.", "\"Remind me to buy groceries at 6 PM\""),
            // Calendar & Tasks
            ToolItem("create_calendar_event", "Calendar & Tasks", "Inserts event into Android Calendar provider.", "\"Add meeting tomorrow at 10 AM\""),
            ToolItem("list_tasks", "Calendar & Tasks", "Lists pending tasks from Zara Organizer DB.", "\"What are my tasks for today?\""),
            ToolItem("add_task", "Calendar & Tasks", "Adds new todo task item with priority.", "\"Add task review PR by evening\""),
            // Memory & Intelligence
            ToolItem("save_memory", "Memory & Intelligence", "Explicitly saves personal preferences into Semantic Memory v4.", "\"Remember that my favorite color is purple\""),
            ToolItem("search_memory", "Memory & Intelligence", "Retrieves remembered facts and preferences.", "\"What do you remember about my college?\""),
            ToolItem("get_daily_briefing", "Memory & Intelligence", "Generates consolidated morning audio briefing.", "\"Give me my daily briefing\""),
            // Maps & Navigation
            ToolItem("get_directions", "Maps & Navigation", "Opens Google Maps navigation to destination.", "\"Navigate to Chennai Airport\""),
            ToolItem("find_nearby", "Maps & Navigation", "Locates nearby restaurants, ATMs, or fuel stations.", "\"Find nearby petrol pumps\""),
            // Notifications
            ToolItem("list_notifications", "Notifications", "Lists active unread system notifications.", "\"Check my notifications\""),
            ToolItem("dismiss_notification", "Notifications", "Clears specific notification from drawer.", "\"Dismiss the promo notification\"")
        )

        binding.rvExploreTools.layoutManager = LinearLayoutManager(this)
        binding.rvExploreTools.adapter = object : RecyclerView.Adapter<ToolViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ToolViewHolder {
                val itemBinding = ItemToolActionCardBinding.inflate(LayoutInflater.from(parent.context), parent, false)
                return ToolViewHolder(itemBinding)
            }

            override fun onBindViewHolder(holder: ToolViewHolder, position: Int) {
                holder.bind(toolsList[position])
            }

            override fun getItemCount(): Int = toolsList.size
        }
    }

    class ToolViewHolder(private val itemBinding: ItemToolActionCardBinding) : RecyclerView.ViewHolder(itemBinding.root) {
        fun bind(item: ToolItem) {
            itemBinding.tvToolActionName.text = item.name
            itemBinding.tvToolDescription.text = item.description
            itemBinding.tvToolExamplePrompt.text = item.examplePrompt
            itemBinding.tvToolPermissionBadge.text = item.badge
        }
    }
}
