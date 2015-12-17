package com.lazybird.akka.fsm

/**
 * Created by yangguo on 15/12/17.
 */

import java.util.concurrent.atomic.AtomicInteger

import akka.actor._

import scala.concurrent.duration._

sealed trait State
case object UnInit extends State
case object  PreStart extends State
case object Running extends State
case object Stopping extends State
case object Exited extends State

object CompareProtocol{
  case object Run
  case object PreStart
  case object Stop
  case object End
  case object Exited
  case object GetStateData

}

class CompareFSM extends FSM[State,String]{
  startWith(UnInit,"1.UnInitialed")
  when(UnInit,1.seconds){
    case Event(CompareProtocol.PreStart,_)=>
      goto(PreStart) using "2.pre start state!"
    case Event(CompareProtocol.GetStateData,currentState)=>
      sender() ! currentState+stateName
      stay()
  }
  when(PreStart,1.seconds){
    case Event(CompareProtocol.Run, msg) =>
        goto(Running) using "3.Running State!"
    case Event(CompareProtocol.GetStateData,currentState)=>
      sender() ! currentState+stateName
      stay()
  }
  when(Running){
    case Event(CompareProtocol.Stop,_)=>
      goto(Stopping) using "4.Stopping State!"
    case Event(CompareProtocol.GetStateData,currentState)=>
      sender() ! currentState+stateName
      stay()
  }
  when(Stopping){
    case Event(CompareProtocol.End,_)=>
      goto(Exited) using "5.Exited State!" forMax(500.milliseconds) replying("\t\t goto Exited State")

    case Event(CompareProtocol.GetStateData,currentState)=>
      sender() ! currentState+stateName
      stay()
  }
  when(Exited){
    case Event(CompareProtocol.PreStart,_)=>
      goto(PreStart) using "6.Restart To PrStart State!"
    case Event(CompareProtocol.GetStateData,currentState)=>
      sender() ! (currentState+stateName)
      stay()
  }
  whenUnhandled{
    case Event(event,state)=>
      println(s"unhandled event message received,event[$event],state[$state]")
      stay()
  }
  onTransition{
    case UnInit->PreStart=> println("Start from [UnInit] to [PreStart]")
  }
  initialize()
}
class ResourceManager extends Actor{
  val fsmActor=context.actorOf(Props[CompareFSM])
  import context.dispatcher
  val atomic=new AtomicInteger(0)
  val allStatus=IndexedSeq(CompareProtocol.PreStart,CompareProtocol.Run,CompareProtocol.Stop,CompareProtocol.End)
  @throws[Exception](classOf[Exception])
  override def preStart(): Unit = {
    context.system.scheduler.schedule(0.seconds,2.seconds){
      fsmActor ! CompareProtocol.GetStateData
      fsmActor ! allStatus(atomic.get()%allStatus.size)
      atomic.getAndIncrement()
    }
  }
  override def receive: Receive = {
    case msg=>println(msg)
  }
}
object FSMSimpleExample {
  def main(args: Array[String]) {
    val system = ActorSystem("system")
    system.actorOf(Props[ResourceManager])
  }
}
