package com.nutomic.ensichat.fragments

import android.app.{Activity, ListFragment}
import android.content.{ActivityNotFoundException, Intent}
import android.os.{AsyncTask, Bundle}
import android.preference.PreferenceManager
import android.view.View.OnClickListener
import android.view.inputmethod.EditorInfo
import android.view.{KeyEvent, LayoutInflater, View, ViewGroup}
import android.widget.TextView.OnEditorActionListener
import android.widget._
import com.nutomic.ensichat.R
import com.nutomic.ensichat.activities.EnsiChatActivity
import com.nutomic.ensichat.protocol.ChatService.OnMessageReceivedListener
import com.nutomic.ensichat.protocol.body.{InitiatePayment, PaymentInformation, Text}
import com.nutomic.ensichat.protocol.header.ContentHeader
import com.nutomic.ensichat.protocol.{Address, ChatService, Message}
import com.nutomic.ensichat.util.{Database, MessagesAdapter}
import de.schildbach.wallet.integration.android.BitcoinIntegration

import scala.collection.SortedSet

/**
 * Represents a single chat with another specific device.
 */
class ChatFragment extends ListFragment with OnClickListener
    with OnMessageReceivedListener with OnEditorActionListener {

  private val REQUEST_FETCH_PAYMENT_REQUEST = 1

  /**
   * Fragments need to have a default constructor, so this is optional.
   */
  def this(address: Address) {
    this
    this.contactAddress = address
  }

  private lazy val database = new Database(getActivity)

  private var contactAddress: Address = _

  private var chatService: ChatService = _

  private var sendBitcoinButton: ImageButton = _

  private var sendButton: Button = _

  private var messageText: EditText = _

  private var listView: ListView = _

  private var adapter: ArrayAdapter[Message] = _

  override def onActivityCreated(savedInstanceState: Bundle): Unit = {
    super.onActivityCreated(savedInstanceState)

    val activity = getActivity.asInstanceOf[EnsiChatActivity]
    activity.runOnServiceConnected(() => {
      chatService = activity.service

      database.getContact(contactAddress).foreach(c => getActivity.setTitle(c.name))

      adapter = new MessagesAdapter(getActivity, contactAddress)
      chatService.registerMessageListener(ChatFragment.this)
      // AnyRef as workaround for https://issues.scala-lang.org/browse/SI-1459
      new AsyncTask[AnyRef, Void, AnyRef] {
        override protected def doInBackground(params: AnyRef*): AnyRef =
          database.getMessages(contactAddress, 15)
        override protected def onPostExecute(result: AnyRef) =
          result.asInstanceOf[SortedSet[Message]].foreach(onMessageReceived)
      }.execute()

      if (listView != null) {
        listView.setAdapter(adapter)
      }
    })
  }

  override def onCreateView(inflater: LayoutInflater, container: ViewGroup,
        savedInstanceState: Bundle): View = {
    val view          = inflater.inflate(R.layout.fragment_chat, container, false)
    sendBitcoinButton = view.findViewById(R.id.send_bitcoin).asInstanceOf[ImageButton]
    sendButton        = view.findViewById(R.id.send).asInstanceOf[Button]
    messageText       = view.findViewById(R.id.message).asInstanceOf[EditText]
    listView          = view.findViewById(android.R.id.list).asInstanceOf[ListView]

    sendBitcoinButton.setOnClickListener(this)
    sendButton.setOnClickListener(this)
    messageText.setOnEditorActionListener(this)
    listView.setAdapter(adapter)

    view
  }

  override def onCreate(savedInstanceState: Bundle): Unit = {
    super.onCreate(savedInstanceState)

    if (savedInstanceState != null)
      contactAddress = new Address(savedInstanceState.getByteArray("device"))
  }

  override def onSaveInstanceState(outState: Bundle): Unit = {
    super.onSaveInstanceState(outState)
    outState.putByteArray("device", contactAddress.bytes)
  }

  override def onEditorAction(view: TextView, actionId: Int, event: KeyEvent): Boolean = {
    if (actionId == EditorInfo.IME_ACTION_DONE) {
      onClick(sendButton)
      true
    } else
      false
  }

  /**
   * Send message if send button was clicked.
   */
  override def onClick(view: View): Unit = view.getId match {
    case R.id.send_bitcoin =>
      chatService.sendTo(contactAddress, new InitiatePayment())
    case R.id.send =>
      val text = messageText.getText.toString.trim
      if (!text.isEmpty) {
        val message = new Text(text)
        chatService.sendTo(contactAddress, message)
        messageText.getText.clear()
      }
  }

  /**
   * Displays new messages in UI.
   */
  override def onMessageReceived(msg: Message): Unit = {
    if (getActivity == null)
      return

    if (!Set(msg.header.origin, msg.header.target).contains(contactAddress))
      return

    val types: Set[Class[_]] =
      Set(classOf[Text], classOf[InitiatePayment], classOf[PaymentInformation])
    if (!types.contains(msg.body.getClass))
      return

    adapter.add(msg)

    val header = msg.header.asInstanceOf[ContentHeader]
    if (msg.header.origin != contactAddress || header.read)
      return

    database.setMessageRead(header)

    // Special handling for Bitcoin messages.
    msg.body match {
      case _: Text =>
      case _: InitiatePayment =>
        val pm = PreferenceManager.getDefaultSharedPreferences(getActivity)

        val wallet = pm.getString(SettingsFragment.KeyBitcoinWallet,
          getString(R.string.default_bitcoin_wallet))
        val intent = new Intent()
        intent.setClassName(wallet, "de.schildbach.wallet.ui.FetchPaymentRequestActivity")
        intent.putExtra("sender_name", chatService.getUser(msg.header.origin).name)
        try {
          startActivityForResult(intent, REQUEST_FETCH_PAYMENT_REQUEST)
        } catch {
          case e: ActivityNotFoundException =>
            Toast.makeText(getActivity, R.string.bitcoin_wallet_not_found, Toast.LENGTH_LONG).show();
        }
      case pr: PaymentInformation =>
        BitcoinIntegration.request(getActivity, pr.bytes)
    }
  }

  override def onActivityResult(requestCode: Int, resultCode: Int, data: Intent): Unit = requestCode match {
    case REQUEST_FETCH_PAYMENT_REQUEST =>
      if (resultCode == Activity.RESULT_OK) {
        val pr = new PaymentInformation(data.getByteArrayExtra("payment_request"))
        chatService.sendTo(contactAddress, pr)
      }
  }

}
