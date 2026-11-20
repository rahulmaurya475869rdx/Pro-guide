package com.example.proguide

import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class SavedRoutesActivity : AppCompatActivity() {

    private lateinit var rvSavedRoutes: RecyclerView
    private lateinit var tvNoRoutes: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_saved_routes)

        rvSavedRoutes = findViewById(R.id.rvSavedRoutes)
        tvNoRoutes = findViewById(R.id.tvNoRoutes)

        rvSavedRoutes.layoutManager = LinearLayoutManager(this)
        loadRoutes()
    }

    private fun loadRoutes() {
        val routes = RouteFileManager.getAllSavedRoutes(this)
        if (routes.isEmpty()) {
            tvNoRoutes.visibility = View.VISIBLE
            rvSavedRoutes.visibility = View.GONE
        } else {
            tvNoRoutes.visibility = View.GONE
            rvSavedRoutes.visibility = View.VISIBLE
            rvSavedRoutes.adapter = RouteAdapter(routes)
        }
    }

    inner class RouteAdapter(private val routes: List<SavedRoute>) : RecyclerView.Adapter<RouteAdapter.RouteViewHolder>() {

        inner class RouteViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
            val tvName: TextView = itemView.findViewById(R.id.tvRouteName)
            val tvDate: TextView = itemView.findViewById(R.id.tvRouteDate)
            val tvDesc: TextView = itemView.findViewById(R.id.tvRouteDesc)
            val btnLoad: Button = itemView.findViewById(R.id.btnLoadRoute)
            val btnDelete: Button = itemView.findViewById(R.id.btnDeleteRoute)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RouteViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_saved_route, parent, false)
            return RouteViewHolder(view)
        }

        override fun onBindViewHolder(holder: RouteViewHolder, position: Int) {
            val route = routes[position]
            holder.tvName.text = route.name
            holder.tvDesc.text = if (route.description.isNotBlank()) route.description else "No description"
            
            val sdf = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
            holder.tvDate.text = sdf.format(Date(route.timestamp))

            holder.btnLoad.setOnClickListener {
                val resultIntent = Intent()
                resultIntent.putExtra("load_route_file", route.fileName)
                setResult(RESULT_OK, resultIntent)
                finish()
            }

            holder.btnDelete.setOnClickListener {
                AlertDialog.Builder(this@SavedRoutesActivity)
                    .setTitle("Delete Route")
                    .setMessage("Kya aap sach me '${route.name}' ko delete karna chahte hain?")
                    .setPositiveButton("Yes") { _, _ ->
                        RouteFileManager.deleteRoute(this@SavedRoutesActivity, route.fileName)
                        loadRoutes() 
                    }
                    .setNegativeButton("No", null)
                    .show()
            }
        }

        override fun getItemCount() = routes.size
    }
}
