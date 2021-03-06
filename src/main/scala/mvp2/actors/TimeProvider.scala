package mvp2.actors

import java.net.InetAddress
import akka.actor.ActorSelection
import mvp2.data.InnerMessages.TimeDelta
import mvp2.utils.Settings
import org.apache.commons.net.ntp.{NTPUDPClient, TimeInfo}
import scala.concurrent.ExecutionContext.Implicits.global
import scala.concurrent.duration._
import scala.language.postfixOps
import scala.util.Try

class TimeProvider(settings: Settings) extends CommonActor {

  val client: NTPUDPClient = new NTPUDPClient()
  client.setDefaultTimeout(settings.ntp.timeout)

  val actors: Seq[ActorSelection] = Seq(
    context.actorSelection("/user/starter/blockchainer"),
    context.actorSelection("/user/starter/blockchainer/publisher"),
    context.actorSelection("/user/starter/influxActor")
  )

  context.system.scheduler.schedule(1 seconds, settings.ntp.updateEvery.millisecond)(
    Try {
      client.open()
      val info: TimeInfo = client.getTime(InetAddress.getByName(settings.ntp.server))
      client.close()
      info.computeDetails()
      info.getOffset
    }.recover {
      case e: Throwable =>
        logger.error(s"Error during updating time-delta: ${e.getMessage}.")
        throw e
    }.foreach(delta => actors.foreach(_ ! TimeDelta(delta)))
  )

  override def specialBehavior: Receive = {
    case _ =>
  }
}
