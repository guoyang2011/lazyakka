package com.lazybird.akka.cluster

import java.util.UUID
import java.util.concurrent.atomic.AtomicInteger

import akka.actor._
import akka.cluster.ClusterEvent._
import akka.cluster.{Cluster, Member}
import com.typesafe.config.ConfigFactory

case class FTaskContext(parent:ActorRef,var fCount:Int,val number:Int,var value:BigDecimal)
case class FResult(jobId:String,number:Int,value:BigDecimal,isCache:Boolean)
case class FRequest(jobId:String,num:Int)
case object GetCache
class FibExecutor extends Actor {
  import FibExecutor._
  val cluster = Cluster(context.system)
  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    cluster.subscribe(self, initialStateMode = InitialStateAsSnapshot, classOf[MemberEvent], classOf[UnreachableMember])
  }
  @throws[Exception](classOf[Exception])
  override def postStop(): Unit = cluster.unsubscribe(self)
  override def receive: Receive = {
    case GetCache=>sender() ! cache
    case init:CurrentClusterState=>init.members.foreach{member=>if (!members.contains(member)) members = members :+ member}
    case MemberUp(member) => if (!members.contains(member))  members = members :+ member
    case MemberRemoved(member, status) =>
      members = members.filterNot(_ == member)
    case UnreachableMember(member) =>
      members = members.filterNot(_ == member)
      cluster.down(member.address)
    case _result:FResult => {
      currentTask.get(_result.jobId) match {
        case Some(task) => {
          task.fCount += 1
          task.value += _result.value
          if (task.fCount == 2) {
            cache = cache + (task.number -> task.value)
            currentTask = currentTask - _result.jobId
            task.parent ! FResult(_result.jobId,task.number, task.value,false)
          }
        }
        case None => //do nothing
      }
    }
    case FRequest(jobId, num) => {
      if (num == 1 || num == 2) sender() ! FResult(jobId,num, 1,false)
      else if(num>2){
        cache.get(num) match {
          case Some(value) => sender() ! FResult(jobId, num,value,true)
          case None => {
            val taskId = UUID.randomUUID().toString
            val taskContext = FTaskContext( sender(),0,num,0)
            currentTask = currentTask + (taskId -> taskContext)
            if (members.isEmpty) {
              sender() ! FRequest(taskId, num - 1)
              sender() ! FRequest(taskId, num - 2)
            } else {
              (1 to 2).foreach { index =>
                context.actorSelection(RootActorPath(members(atomic.getAndIncrement() % members.size).address) / "user" / "*") ! FRequest(taskId, num - index)
              }
            }
          }
        }
      }else{
        sender() ! "Number Must >0"
      }
    }
  }
}
object FibExecutor{
  var currentTask = Map.empty[String, FTaskContext]
  var cache = Map.empty[Int, BigDecimal]
  var members = IndexedSeq.empty[Member]
  var atomic = new AtomicInteger(0)
}

class FrontFibExecutor(schedulerTime:Int) extends Actor{
  import scala.concurrent.duration._
  import context.dispatcher
  var atomic=1
  context.system.scheduler.schedule(1.seconds,schedulerTime.microseconds){
    val actorSelector=context.actorSelection("akka.tcp://fsystem@localhost:10001/user/*")
    actorSelector! FRequest(s"jobid-${System.currentTimeMillis()}-${atomic}",atomic)
    atomic += 1
  }
  override def receive: Actor.Receive = {
    case result:FResult=>println(result)
    case msg=>println("undefined message:"+msg)

  }
}
object FStart{
  def main(args: Array[String]) {
    val port= args(0).toInt
    val confFile=args(2)
    val conf=ConfigFactory
      .parseString(s"akka.remote.netty.tcp.port=$port")
      .withFallback(ConfigFactory.parseFile(
        new java.io.File(getClass.getClassLoader.getResource(confFile).getFile
      )))
    val system=ActorSystem("fsystem",conf)
    if(args(1).equalsIgnoreCase("cluster-worker")){//ClusterActorProvider
      system.actorOf(Props[FibExecutor])
    }else{//RemoteActorProvider
      system.actorOf(Props(new FrontFibExecutor(200)))
    }
  }
}