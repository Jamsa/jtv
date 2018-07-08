package com.github.jamsa.jtv.client.manager

import java.awt.event.InputEvent
import java.awt.image.BufferedImage
import java.util.Observable

import com.github.jamsa.jtv.client.capture.ScreenCapture
import com.github.jamsa.jtv.client.network.Client
import com.github.jamsa.jtv.common.model.{ControlRequest, LoginRequest, LoginResponse}
import com.github.jamsa.jtv.common.utils.ChannelUtils
import com.typesafe.scalalogging.Logger
import io.netty.channel.{Channel, ChannelFuture, ChannelHandlerContext}

import scala.collection.mutable

/*
object JtvManagerEventType extends Enumeration{
  type JtvManagerEventType = Value
  val SCREEN_CAPTURE=Value
}
case class JtvManagerEvent()
*/

object ClientSessionManager{
  val logger  = Logger(ClientSessionManager.getClass)

  private var sessionChannel:Option[Channel]=None
  private val workChannels = mutable.Map[String,Channel]()

  def getSessionId = sessionChannel.flatMap(ChannelUtils.getSessionId(_))

  def createSession():Option[Channel] = {
    destroySession()
    sessionChannel = this.createConnection()
    sessionChannel.foreach(channel => {
      logger.info(s"${channel.id().asLongText()}被设置为会话连接")
    })
    sessionChannel
  }

  def setSessionId(sessionId:Int)={

    sessionChannel.foreach(channel => {
      logger.info(s"设置会话id为：${sessionId}")
      ChannelUtils.setSessionId(channel,sessionId)
      workChannels.remove(channel.id().asLongText())
    })

  }

  def createConnection()={
    val result = Client.createChannel

    result.foreach( ch => {
      logger.info(s"新建连接:${ch.id().asLongText()}")
      getSessionId.foreach(ChannelUtils.setSessionId(ch,_))
      workChannels.put(ch.id().asLongText(),ch)
      ch.closeFuture().addListener((future:ChannelFuture) =>{
        logger.info(s"工作连接${future.channel().id().asLongText()}被关闭")
        workChannels.remove(future.channel().id().asLongText())
      })
    })
    result
  }

  def destroySession()={
    workChannels.values.foreach(_.close().sync())
    workChannels.clear()
    sessionChannel.foreach(_.close().sync())
    sessionChannel = None
  }

  def getSessionChannel()={

    sessionChannel
  }
}

object JtvClientManager extends Observable{
  val logger  = Logger(JtvClientManager.getClass)

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

  def loginReq(username:String,password:String): Unit ={
    ClientSessionManager.createSession().foreach(_.writeAndFlush(LoginRequest(username,password)))
  }

  def loginResp(ctx:ChannelHandlerContext,loginResponse: LoginResponse): Unit ={
    logger.info(s"登录响应${loginResponse}")
    ClientSessionManager.setSessionId(loginResponse.sessionId)
  }

  def main(args: Array[String]): Unit = {
    Client.startup("localhost",10090)

    JtvClientManager.loginReq("","")
    Thread.sleep(3000)
    ClientSessionManager.getSessionId.foreach(sessionId => {
      ClientSessionManager.createConnection().foreach(_.writeAndFlush(ControlRequest(3, "3",sessionId, None)))
    })
    Client.shutdown()
  }
}