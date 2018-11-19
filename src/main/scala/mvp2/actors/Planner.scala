package mvp2.actors

import java.security.PublicKey

import akka.actor.{ActorSelection, Cancellable}
import mvp2.data.InnerMessages.{Get, PeerPublicKey}
import mvp2.data.KeyBlock
import mvp2.utils.{ECDSA, EncodingUtils, Settings}

import scala.collection.immutable.SortedSet
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

class Planner(settings: Settings) extends CommonActor {

  import Planner.{Period, Tick}

  var allPublicKeys: SortedSet[PublicKey] = SortedSet.empty[PublicKey]
  var nextPeriod: Period = Period(KeyBlock(), settings)
  val heartBeat: Cancellable =
    context.system.scheduler.schedule(0 seconds, settings.plannerHeartbeat milliseconds, self, Tick)
  val publisher: ActorSelection = context.system.actorSelection("/user/starter/blockchainer/publisher")

  override def specialBehavior: Receive = {
    case keyBlock: KeyBlock =>
      logger.info(s"Planner received new keyBlock with height: ${keyBlock.height}.")
      nextPeriod = Period(keyBlock, settings)
      context.parent ! nextPeriod
    case PeerPublicKey(key) =>
      logger.info(s"Got public key from remote: ${EncodingUtils.encode2Base16(ECDSA.compressPublicKey(key))} on Planner.")
      allPublicKeys = allPublicKeys + key
    case Tick if nextPeriod.timeToPublish =>
      publisher ! Get
      logger.info("Planner sent publisher request: time to publish!")
    case Tick if nextPeriod.noBlocksInTime =>
      val newPeriod = Period(nextPeriod, settings)
      logger.info(s"No blocks in time. Planner added ${newPeriod.exactTime - System.currentTimeMillis} milliseconds.")
      nextPeriod = newPeriod
    case Tick =>
  }
}

object Planner {

  case class Period(begin: Long, exactTime: Long, end: Long) {
    def timeToPublish: Boolean = {
      val now: Long = System.currentTimeMillis
      now >= this.begin && now <= this.end
    }

    def noBlocksInTime: Boolean = System.currentTimeMillis > this.end

  }

  object Period {

    def apply(lastKeyBlock: KeyBlock, settings: Settings): Period = {
      val exactTimestamp: Long = lastKeyBlock.timestamp + settings.blockPeriod
      Period(exactTimestamp - settings.biasForBlockPeriod, exactTimestamp, exactTimestamp + settings.biasForBlockPeriod)
    }

    def apply(previousPeriod: Period, settings: Settings): Period = {
      val exactTimestamp: Long = previousPeriod.exactTime + settings.blockPeriod / 2
      Period(exactTimestamp - settings.biasForBlockPeriod, exactTimestamp, exactTimestamp + settings.biasForBlockPeriod)
    }
  }

  case class Epoch(lastKeyBlock: KeyBlock) {
    val epochMultiplier: Int = 2
  }

  case object Tick

}