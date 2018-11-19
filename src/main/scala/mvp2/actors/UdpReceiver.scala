package mvp2.actors

import java.net.{InetAddress, InetSocketAddress}
import akka.actor.{Actor, ActorRef, ActorSelection}
import akka.io.{IO, Udp}
import akka.serialization.{Serialization, SerializationExtension}
import akka.util.ByteString
import com.typesafe.scalalogging.StrictLogging
import mvp2.MVP2.system
import mvp2.data.InnerMessages.{MsgFromNetwork, UdpSocket}
import mvp2.data.NetworkMessages._
import mvp2.utils.{EncodingUtils, Settings, Sha256}

class UdpReceiver(settings: Settings) extends Actor with StrictLogging {

  val serialization: Serialization = SerializationExtension(context.system)

  val udpSender: ActorSelection = context.actorSelection("/user/starter/blockchainer/networker/udpSender")

  val influxActor: ActorSelection = context.actorSelection("/user/starter/influxActor")

  val myAddr: InetSocketAddress = new InetSocketAddress(InetAddress.getLocalHost.getHostAddress, settings.port)

  override def preStart(): Unit = {
    logger.info("Starting the Receiver!")
    IO(Udp) ! Udp.Bind(self, myAddr)
  }

  override def receive: Receive = {
    case Udp.Bound(local) =>
      logger.info(s"Binded to $local")
      context.become(readCycle(sender))
      udpSender ! UdpSocket(sender)
    case msg => logger.info(s"Received message $msg from $sender before binding")
  }

  def readCycle(socket: ActorRef): Receive = {
    case Udp.Received(data: ByteString, remote) =>
      deserialize(data).foreach { message =>
        logger.info(s"Received $message from $remote")
        context.parent ! MsgFromNetwork(message, remote)
        context.actorSelection("/user/starter/influxActor") !
          MsgFromNetwork(
            message,
            remote,
            Sha256.toSha256(EncodingUtils.encode2Base16(data) ++ myAddr.getAddress.toString)
          )
      }
    case Udp.Unbind =>
      socket ! Udp.Unbind
      logger.info(s"Unbind $socket")
    case Udp.Unbound =>
      logger.info(s"Unbound $socket")
      context.stop(self)
  }


  def deserialize(bytes: ByteString): Option[NetworkMessage] = bytes.head match {
    case NetworkMessagesId.PeersId => Option(serialization.findSerializerFor(Peers).fromBinary(bytes.toArray.tail))
      .map {
        case knownPeers: Peers => knownPeers
      }
    case NetworkMessagesId.BlocksId => Option(serialization.findSerializerFor(Blocks).fromBinary(bytes.toArray.tail))
      .map {
        case blocks: Blocks => blocks
      }
    case NetworkMessagesId.SyncMessageIteratorsId =>
      Option(serialization.findSerializerFor(SyncMessageIterators).fromBinary(bytes.toArray.tail)).map {
        case iterators: SyncMessageIterators => iterators
      }
    case NetworkMessagesId.TransactionsId =>
      Option(serialization.findSerializerFor(Transactions).fromBinary(bytes.toArray.tail)).map {
        case txs: Transactions => txs
      }
    case NetworkMessagesId.LastBlockHeightId =>
      Option(serialization.findSerializerFor(LastBlockHeight).fromBinary(bytes.toArray.tail)).map {
        case lastBlockHeight: LastBlockHeight => lastBlockHeight
      }
  }
}