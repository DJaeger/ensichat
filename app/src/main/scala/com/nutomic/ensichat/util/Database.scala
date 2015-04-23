package com.nutomic.ensichat.util

import java.util.Date

import android.content.{ContentValues, Context}
import android.database.sqlite.{SQLiteDatabase, SQLiteOpenHelper}
import com.nutomic.ensichat.protocol.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.protocol._
import com.nutomic.ensichat.protocol.body._
import com.nutomic.ensichat.protocol.header.ContentHeader
import com.nutomic.ensichat.util.Database.OnContactsUpdatedListener

import scala.collection.immutable.TreeSet
import scala.collection.{SortedSet, mutable}

object Database {

  private val DatabaseName = "message_store.db"

  private val DatabaseVersion = 1

  // NOTE: We could make origin/target foreign keys to contacts, but:
  // - they don't change anyway
  // - we'd have to insert the local user into contacts
  private val CreateTableMessages = "CREATE TABLE messages(" +
    "_id INTEGER PRIMARY KEY," +
    "origin TEXT NOT NULL," +
    "target TEXT NOT NULL," +
    "message_id INT NOT NULL," +
    "type INT NOT NULL," +
    "date INT NOT NULL," + // Unix timestamp
    "read INT NOT NULL);"

  private val CreateTableTexts = "CREATE TABLE texts(" +
    "_id INTEGER PRIMARY KEY," +
    "message_id INT," +
    "text TEXT NOT NULL," +
    "FOREIGN KEY (message_id) REFERENCES messages(_id))"

  private val CreateTablePaymentRequests = "CREATE TABLE payment_requests(" +
    "_id INTEGER PRIMARY KEY," +
    "message_id INT," +
    "bytes TEXT NOT NULL," +
    "FOREIGN KEY (message_id) REFERENCES messages(_id))"

  private val CreateTableContacts = "CREATE TABLE contacts(" +
    "_id INTEGER PRIMARY KEY," +
    "address TEXT NOT NULL," +
    "name TEXT NOT NULL)"

  trait OnContactsUpdatedListener {
    def onContactsUpdated()
  }

  private var contactsUpdatedListeners =
    new mutable.WeakHashMap[OnContactsUpdatedListener, Unit].keySet

}

/**
 * Stores all messages and contacts in SQL database.
 */
class Database(context: Context)
  extends SQLiteOpenHelper(context, Database.DatabaseName, null, Database.DatabaseVersion)
  with OnMessageReceivedListener {

  override def onConfigure(db: SQLiteDatabase): Unit = {
    db.execSQL("PRAGMA foreign_keys = ON;")
  }

  override def onCreate(db: SQLiteDatabase): Unit = {
    db.execSQL(Database.CreateTableMessages)
    db.execSQL(Database.CreateTableTexts)
    db.execSQL(Database.CreateTablePaymentRequests)
    db.execSQL(Database.CreateTableContacts)
  }

  /**
   * Returns the count last messages for device.
   */
  def getMessages(address: Address, count: Int): SortedSet[Message] = {
    val c = getReadableDatabase.query("messages",
      Array("_id", "origin", "target", "message_id", "type", "date", "read"), "origin=? OR target=?",
      Array(address.toString, address.toString), null, null, "date DESC", count.toString)
    var messages = new TreeSet[Message]()(Message.Ordering)
    while (c.moveToNext()) {
      val header = new ContentHeader(
        new Address(c.getString(c.getColumnIndex("origin"))),
        new Address(c.getString(c.getColumnIndex("target"))),
        -1,
        c.getInt(c.getColumnIndex("type")),
        c.getLong(c.getColumnIndex("message_id")),
        new Date(c.getLong(c.getColumnIndex("date"))),
        c.getInt(c.getColumnIndex("read")) == 1)

      val id = c.getString(c.getColumnIndex("_id"))
      val body = header.contentType match {
        case Text.Type =>
          val c2 = getReadableDatabase.query(
            "texts", Array("text"), "message_id=?", Array(id), null, null, null)
          c2.moveToFirst()
          new Text(new String(c2.getString(c2.getColumnIndex ("text"))))
        case InitiatePayment.Type =>
          new InitiatePayment()
        case PaymentInformation.Type =>
          val c2 = getReadableDatabase.query(
            "payment_requests", Array("bytes"), "message_id=?", Array(id), null, null, null)
          c2.moveToFirst()
          new PaymentInformation(c2.getBlob(c2.getColumnIndex("bytes")))
      }
      messages += new Message(header, body)
    }
    c.close()
    messages
  }

  /**
   * Inserts the given new message into the database.
   */
  override def onMessageReceived(msg: Message): Unit = {
    // Only certain types of messages are stored.
    val types: Set[Class[_]] =
      Set(classOf[Text], classOf[InitiatePayment], classOf[PaymentInformation])
    if (!types.contains(msg.body.getClass))
        return

    val header = msg.header.asInstanceOf[ContentHeader]
    val cv =  new ContentValues()
    cv.put("origin",     header.origin.toString)
    cv.put("target",     header.target.toString)
    // Need to use [[Long#toString]] because of https://issues.scala-lang.org/browse/SI-2991
    cv.put("message_id", header.messageId.toString)
    cv.put("type",       header.contentType.toString)
    cv.put("date",       header.time.getTime.toString)
    cv.put("read",       header.read)

    val id = getWritableDatabase.insert("messages", null, cv)

    val cvExtra = new ContentValues()
    cvExtra.put("message_id", id.toString)
    msg.body match {
      case text: Text =>
        cvExtra.put("text", text.text)
        getWritableDatabase.insert("texts", null, cvExtra)
      case pr: PaymentInformation =>
        cvExtra.put("bytes", pr.bytes)
        getWritableDatabase.insert("payment_requests", null, cvExtra)
      case _: InitiatePayment =>
    }
  }

  /**
   * Marks the message as read by the user.
   */
  def setMessageRead(header: ContentHeader): Unit = {
    val cv = new ContentValues()
    cv.put("read", "1")
    getReadableDatabase.update("messages", cv, "origin=? AND message_id=?",
      Array(header.origin.toString, header.messageId.toString))
  }

  /**
   * Returns all contacts of this user.
   */
  def getContacts: Set[User] = {
    val c = getReadableDatabase.query(true, "contacts", Array("address", "name"), "", Array(),
      null, null, null, null)
    var contacts = Set[User]()
    while (c.moveToNext()) {
      contacts += new User(new Address(c.getString(c.getColumnIndex("address"))),
                              c.getString(c.getColumnIndex("name")))
    }
    c.close()
    contacts
  }

  /**
   * Returns the contact with the given address if it exists.
   */
  def getContact(address: Address): Option[User] = {
    val c = getReadableDatabase.query(true, "contacts", Array("address", "name"), "address = ?",
      Array(address.toString), null, null, null, null)
    if (c.getCount != 0) {
      c.moveToNext()
      val s = Some(new User(new Address(c.getString(c.getColumnIndex("address"))),
        c.getString(c.getColumnIndex("name"))))
      c.close()
      s
    } else {
      c.close()
      None
    }
  }

  /**
   * Inserts the given device into contacts.
   */
  def addContact(contact: User): Unit = {
    val cv = new ContentValues()
    cv.put("address", contact.address.toString)
    cv.put("name", contact.name.toString)
    getWritableDatabase.insert("contacts", null, cv)
    Database.contactsUpdatedListeners.foreach(_.onContactsUpdated()    )
  }
  
  def changeContactName(contact: User): Unit = {
    val cv = new ContentValues()
    cv.put("name", contact.name.toString)
    getWritableDatabase.update("contacts", cv, "address = ?", Array(contact.address.toString))
    Database.contactsUpdatedListeners.foreach(_.onContactsUpdated())
  }

  /**
   * Pass a callback that is called whenever a new contact is added.
   */
  def runOnContactsUpdated(listener: OnContactsUpdatedListener) =
    Database.contactsUpdatedListeners += listener

  override def onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int): Unit = {
  }

}
