package mvp2.actors

import java.security.{KeyPair, PublicKey}
import mvp2.data.InnerMessages.{Get, MyPublicKey, PeerPublicKey}
import mvp2.utils.{ECDSA, EncodingUtils}

class KeyKeeper extends CommonActor {

  val myKeys: KeyPair = ECDSA.createKeyPair

  var allPublicKeys: Set[PublicKey] = Set.empty[PublicKey]

  override def preStart(): Unit = {
    logger.info(s"My public key is: ${EncodingUtils.encode2Base16(ECDSA.compressPublicKey(myKeys.getPublic))}")
    context.actorSelection("/user/starter/blockchainer/networker") ! MyPublicKey(myKeys.getPublic)
    context.actorSelection("/user/starter/blockchainer/planner") ! MyPublicKey(myKeys.getPublic)
  }

  override def specialBehavior: Receive = {
    case Get => sender ! myKeys.getPublic
    case PeerPublicKey(key) =>
      logger.info(s"Got public key from remote: ${EncodingUtils.encode2Base16(ECDSA.compressPublicKey(key))} on KeyKeeper.")
      allPublicKeys = allPublicKeys + key
  }
}
