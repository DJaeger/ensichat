package com.nutomic.ensichat.util

import android.app.{Notification, NotificationManager, PendingIntent}
import android.content.{Context, Intent}
import android.support.v4.app.NotificationCompat
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.MainActivity
import com.nutomic.ensichat.protocol.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.protocol.body.{InitiatePayment, PaymentInformation, Text}
import com.nutomic.ensichat.protocol.{Crypto, Message}

/**
 * Displays notifications for new messages.
 */
class NotificationHandler(context: Context) extends OnMessageReceivedListener {

  private val notificationIdNewMessage = 1

  def onMessageReceived(msg: Message): Unit = {
    if (msg.header.origin == new Crypto(context).localAddress)
      return

    showNotification(msg.body match {
      case t: Text               => t.text
      case _: InitiatePayment    => "InitiatePayment"
      case _: PaymentInformation => "PaymentInformation"
      case _ => return
    })
  }

  private def showNotification(text: String): Unit = {
    val pi = PendingIntent.getActivity(context, 0, new Intent(context, classOf[MainActivity]), 0)
    val notification = new NotificationCompat.Builder(context)
      .setSmallIcon(R.drawable.ic_launcher)
      .setContentTitle(context.getString(R.string.notification_message))
      .setContentText(text)
      .setDefaults(Notification.DEFAULT_ALL)
      .setContentIntent(pi)
      .setAutoCancel(true)
      .build()
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE)
      .asInstanceOf[NotificationManager]
    nm.notify(notificationIdNewMessage, notification)
  }

}
