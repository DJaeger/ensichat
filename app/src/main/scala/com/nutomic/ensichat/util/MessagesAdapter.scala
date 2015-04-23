package com.nutomic.ensichat.util

import android.content.Context
import android.view.{Gravity, View, ViewGroup}
import android.widget.{ArrayAdapter, RelativeLayout, TextView}
import com.nutomic.ensichat.R
import com.nutomic.ensichat.protocol.body.{InitiatePayment, PaymentInformation, Text}
import com.nutomic.ensichat.protocol.{Address, Message}

/**
 * Displays [[Message]]s in ListView.
 */
class MessagesAdapter(context: Context, remoteAddress: Address) extends
  ArrayAdapter[Message](context, R.layout.item_message, android.R.id.text1) {

  /**
   * Free space to the right/left to a message depending on who sent it, in dip.
   */
  private val MessageMargin = 50

  override def getView(position: Int, convertView: View, parent: ViewGroup): View = {
    val view = super.getView(position, convertView, parent).asInstanceOf[RelativeLayout]
    val tv = view.findViewById(android.R.id.text1).asInstanceOf[TextView]

    getItem(position).body match {
      case t: Text => tv.setText(t.text)
      // TODO: proper design, show amount?
      case ip: InitiatePayment => tv.setText("InitiatePayment")
      case pr: PaymentInformation => tv.setText("PaymentInformation")
    }

    val lp = new RelativeLayout.LayoutParams(tv.getLayoutParams)
    val margin = (MessageMargin * context.getResources.getDisplayMetrics.density).toInt
    if (getItem(position).header.origin != remoteAddress) {
      view.setGravity(Gravity.RIGHT)
      lp.setMargins(margin, 0, 0, 0)
    } else {
      view.setGravity(Gravity.LEFT)
      lp.setMargins(0, 0, margin, 0)
    }
    tv.setLayoutParams(lp)

    view
  }

}
