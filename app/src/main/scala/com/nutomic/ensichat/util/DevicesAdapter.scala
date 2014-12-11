package com.nutomic.ensichat.util

import android.content.Context
import android.view.{View, ViewGroup}
import android.widget.{ArrayAdapter, TextView}
import com.nutomic.ensichat.bluetooth.Device
import com.nutomic.ensichat.protocol.Address

/**
 * Displays [[Device]]s in ListView.
 */
class DevicesAdapter(context: Context) extends
  ArrayAdapter[Address](context, android.R.layout.simple_list_item_1) {

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val view = super.getView(position, convertView, parent)
    val title: TextView = view.findViewById(android.R.id.text1).asInstanceOf[TextView]
    title.setText(getItem(position).toString)
    view
  }

}
