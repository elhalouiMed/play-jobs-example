package services

import actors.JobManager
import akka.actor._
import javax.inject._

import scala.concurrent.duration._
import akka.NotUsed
import akka.stream.{ActorMaterializer, Materializer, OverflowStrategy, ThrottleMode}
import akka.stream.scaladsl.Sink
import akka.stream.scaladsl.Source
import play.api.Logger
import play.api.inject.ApplicationLifecycle
import akka.cluster.Cluster

import scala.concurrent.Future

@Singleton
class JobService @Inject() (lifecycle: ApplicationLifecycle)(implicit system: ActorSystem, mat: Materializer)  {
  //import _root_.util.AkkaSupport._

  val cluster = Cluster(system)

  cluster.registerOnMemberUp {
    Logger.debug("Member is ready.")
  }

  cluster.registerOnMemberRemoved {
    Logger.debug("Member is down, stopping actor system.")
    system.terminate()
  }

  lifecycle.addStopHook { () =>
    Future.successful(system.terminate())
  }

  val jobManager = system actorOf Props(new JobManager())


  val throttler = Source.actorRef[JobManager.Msg](bufferSize = 100000, OverflowStrategy.dropNew)
    .throttle(
      elements = 1000, // 1000 messages per second
      per = 1 second,
      maximumBurst = 10,
      mode = ThrottleMode.Shaping
    )
    .to(Sink.actorRef(jobManager, NotUsed))
    .run()

  def onTask(sid: String, info: String) = {
    throttler ! JobManager.TaskComplete(sid, info)

  }

}

