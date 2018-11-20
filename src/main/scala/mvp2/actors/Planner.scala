package mvp2.actors

import java.security.PublicKey

import akka.actor.{ActorSelection, Cancellable}
import mvp2.actors.Planner.Epoch
import mvp2.data.InnerMessages.{Get, MyPublicKey, PeerPublicKey}
import mvp2.data.KeyBlock
import mvp2.utils.{ECDSA, EncodingUtils, Settings}
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.concurrent.ExecutionContext.Implicits.global

class Planner(settings: Settings) extends CommonActor {

  import Planner.{Period, Tick}

  var allPublicKeys: Set[PublicKey] = Set.empty[PublicKey]
  var myPublicKey: Option[PublicKey] = None
  var nextPeriod: Period = Period(KeyBlock(), settings)
  var epoch: Epoch = Epoch(Map())
  var lastBlock: KeyBlock = KeyBlock()
  val heartBeat: Cancellable =
    context.system.scheduler.schedule(15.seconds, settings.plannerHeartbeat milliseconds, self, Tick)
  val publisher: ActorSelection = context.system.actorSelection("/user/starter/blockchainer/publisher")

  override def specialBehavior: Receive = {
    case keyBlock: KeyBlock =>
      logger.info(s"Planner received new keyBlock with height: ${keyBlock.height}.")
      nextPeriod = Period(keyBlock, settings)
      lastBlock = keyBlock
      context.parent ! nextPeriod

    case PeerPublicKey(key) =>
      logger.info(s"Got public key from remote: ${EncodingUtils.encode2Base16(ECDSA.compressPublicKey(key))} on Planner.")
      allPublicKeys = allPublicKeys + key

    case MyPublicKey(key) =>
      allPublicKeys = allPublicKeys + key
      myPublicKey = Some(key)
      epoch = epoch.copy(Map(1 -> key))
    case Tick if epoch.isDone =>
      epoch = epoch(lastBlock, allPublicKeys)
      if (epoch.nextBlock._2 == myPublicKey ) publisher ! epoch.nextBlock
      else context.parent ! epoch.nextBlock

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

  case class Epoch(schedule: Map[Long, PublicKey]) {

    def nextBlock: (Long, PublicKey) = schedule.head

    def delete: Epoch = this.copy(schedule - schedule.head._1)

    def delete(height: Long): Epoch = this.copy(schedule = schedule.drop(height.toInt))

    def noBlockInTime: Epoch = this.copy((schedule - schedule.head._1).map(each => (each._1 - 1, each._2)))

    def isDone: Boolean = this.schedule.isEmpty
  }

  object Epoch {
    def apply(lastKeyBlock: KeyBlock, publicKeys: Set[PublicKey], multiplier: Int = 1): Epoch = {
      val startingHeight: Long = lastKeyBlock.height + 1
      val numberOfBlocksInEpoch: Int = publicKeys.size * multiplier
      var schedule: Map[Long, PublicKey] =
        (for (i <- startingHeight until startingHeight + numberOfBlocksInEpoch)
          yield i).zip(publicKeys).toMap[Long, PublicKey]
      Epoch(schedule)
    }
  }

  case object Tick

}