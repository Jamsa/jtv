package com.github.jamsa.jtv.client.manager

import java.awt.event.InputEvent
import java.awt.image.BufferedImage
import java.util.Observable

import com.github.jamsa.jtv.client.capture.ScreenCapture
import com.github.jamsa.jtv.client.network.Client
import com.github.jamsa.jtv.common.model.{RequestMessage, RequestMessageType}

object JtvManagerEventType extends Enumeration{
  type JtvManagerEventType = Value
  val SCREEN_CAPTURE=Value
}
case class JtvManagerEvent()


object JtvClientManager extends Observable{


  def connect(ip:String,port:Int): Unit ={

  }

  def listener(port:Int): Unit ={

  }

  def sendEvent(event:InputEvent): Unit ={

  }

  def startCapture()={
    ScreenCapture.startCapture()
  }

  def setScreenCapture(bufferedImage: BufferedImage): Unit ={
    this.notifyObservers(bufferedImage)
  }

  def main(args: Array[String]): Unit = {
    Client.startup("localhost",10090)
    Client.send(RequestMessage(RequestMessageType.CONNECT_SERVER,1,"hello",Map{"111" -> "111"}))
    Client.shutdown()
  }
}