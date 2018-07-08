package com.github.jamsa.jtv.server.manager

import java.util.concurrent.atomic.AtomicInteger

import com.github.jamsa.jtv.common.model._
import com.github.jamsa.jtv.common.utils.ChannelUtils
import com.typesafe.scalalogging.Logger
import io.netty.channel._
import io.netty.channel.group.{ChannelGroup, DefaultChannelGroup}
import io.netty.util.concurrent.{GlobalEventExecutor}

import scala.collection.mutable

/**
  * 会话管理器
  */
object ServerSessionManager{
  val logger  = Logger(ServerSessionManager.getClass)
  import collection.JavaConverters._
  //会话id生成器
  private val sid = new AtomicInteger(1)
  //private val channelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
  //会话
  val sessions =  new mutable.WeakHashMap[Int,ChannelId]
  //会话连接
  val sessionChannelGroup = new DefaultChannelGroup(GlobalEventExecutor.INSTANCE)
  //工作连接
  val workerChannelGroups = new mutable.WeakHashMap[Int,ChannelGroup]
  //工作连接
  //val channels = new mutable.WeakHashMap[String,ChannelId]

  //获取新会话id
  private def nextSessionId = sid.getAndAdd(1)

  def createSession(channel:Channel): Int ={
    val sessionId = nextSessionId
    ChannelUtils.setSessionId(channel,sessionId)
    sessionChannelGroup.add(channel)
    sessions.put(sessionId,channel.id())
    workerChannelGroups.put(sessionId,new DefaultChannelGroup(GlobalEventExecutor.INSTANCE))
    //会话连接关闭
    channel.closeFuture().addListener(
      (future:ChannelFuture) => {
        sessionChannelGroup.remove(future.channel().id())
        sessions.remove(sessionId)
        logger.info(s"会话${sessionId}的主连接${future.channel().id().asLongText()}被关闭")

        //关闭工作连接
        workerChannelGroups.get(sessionId).foreach(channelGroup =>{
          channelGroup.writeAndFlush(ErrorMessage("会话关闭!"))
          if(!channelGroup.isEmpty){
            logger.info(s"会话${sessionId}的所工作连接即将被关闭...")
            channelGroup.close()
          }
          workerChannelGroups.remove(sessionId)
        })
      })

    sessionId
  }

  def addWorkChannel(sessionId:Int,channel:Channel)={
    workerChannelGroups.get(sessionId).foreach(channelGroup =>{
      ChannelUtils.setSessionId(channel,sessionId)
      channelGroup.add(channel)

      //工作连接关闭
      channel.closeFuture().addListener((future: ChannelFuture) => {
          logger.info(s"会话${sessionId}的工作连接${future.channel().id().asLongText()}被关闭")
          //从会话工作连接中清除
          ChannelUtils.getSessionId(future.channel()).foreach(workerChannelGroups.get(_).foreach(_.remove(future.channel())))

          //关闭对向连接
          getPairChannel(future.channel()).foreach(channel=>{
            channel.writeAndFlush(ErrorMessage("连接中断!"))
            logger.info(s"会话${sessionId}工作连接${future.channel().id().asLongText()}的连接对${channel.id().asLongText()}即将被关闭...")
            channel.close()
          })
        })
      })
  }

  def closeWorkChannel(sessionId:Int,channel: Channel): Unit ={
    workerChannelGroups.get(sessionId).foreach(_.close())
  }

  def destroySession(sessionId:Int): Unit ={
    sessions.get(sessionId).foreach(channelId=>{
      val channel = sessionChannelGroup.find(channelId)
      logger.info(s"会话${sessionId}的主连接即将被关闭...")
      channel.writeAndFlush(ErrorMessage("会话关闭!"))
      channel.close()
    })

  }

  def getSessionChannel(sessionId:Int) ={
    sessions.get(sessionId).flatMap(channelId => Option(sessionChannelGroup.find(channelId)))
  }

  def getWorkerChannel(sessionId:Int,channelId:String) ={
    workerChannelGroups.get(sessionId).flatMap(_.asScala.find(_.id().asLongText()==channelId))
  }

  private val pairs = mutable.Map[Channel,Channel]()

  def pairChannels(sourceChannel:Channel,targetChannel:Channel): Unit ={
    pairs.put(sourceChannel,targetChannel)
    pairs.put(targetChannel,sourceChannel)
  }

  def getPairChannel(channel:Channel) ={
    pairs.get(channel)
  }
}

/**
  * 服务
  */
object JtvServerManager{
  val logger  = Logger(JtvServerManager.getClass)


  def login(ctx: ChannelHandlerContext,loginRequest: LoginRequest): Unit ={
    val sessionId = ServerSessionManager.createSession(ctx.channel())
    logger.info(s"登录,channelId:${ctx.channel().id().asLongText()}, sessionId:${sessionId}")
    ctx.writeAndFlush(LoginResponse(true,"登录成功",sessionId,"123"))

    //ctx.channel().closeFuture().addListener(_=>ServerSessionManager.destroySession(sessionId))
    /*ctx.channel().closeFuture().addListener(new ChannelFutureListener{
      override def operationComplete(future: ChannelFuture): Unit = {
        ServerSessionManager.destroySession(sessionId)
      }
    })*/
  }

  def logout(ctx: ChannelHandlerContext,logoutRequest: LogoutRequest):Unit={
    val sid = ChannelUtils.getSessionId(ctx.channel())
    logger.info(s"注销,channelId:${ctx.channel().id()}, sessionId:${sid}")
    sid.foreach(sessionId =>{
      ServerSessionManager.destroySession(sessionId)
    })
  }

  def controlReq(ctx: ChannelHandlerContext,controlRequest: ControlRequest): Unit ={
    val sessionId = controlRequest.sourceSessionId
    ChannelUtils.setSessionId(ctx.channel(),sessionId)
    logger.info(s"${sessionId}请求控制，目标session:${controlRequest.targetSessionId}，请求Channel:${ctx.channel().id().asLongText()}")

    ServerSessionManager.addWorkChannel(sessionId,ctx.channel())
    ServerSessionManager.getSessionChannel(controlRequest.targetSessionId) match {
      case Some(channel) => channel.writeAndFlush(controlRequest.copy(sourceChannelId = Some(ctx.channel().id().asLongText())))
      case _ => ctx.channel().writeAndFlush(ErrorMessage("目标会话不存在!"));ctx.close()
    }
  }

  def controlResp(ctx:ChannelHandlerContext,controlResponse: ControlResponse):Unit={
    val sid = ChannelUtils.getSessionId(ctx.channel())
    logger.info(s"${sid}控制响应，源session:${controlResponse.sourceSessionId}，源Channel:${controlResponse.sourceChannelId}")

    sid.foreach(sessionId => {
      ServerSessionManager.addWorkChannel(sessionId,ctx.channel())
    })

    ServerSessionManager.getWorkerChannel(controlResponse.sourceSessionId,controlResponse.sourceChannelId) match{
      case Some(channel) => {
        ServerSessionManager.pairChannels(channel,ctx.channel())
        channel.writeAndFlush(controlResponse)
      }
      case None => ctx.writeAndFlush(ErrorMessage("源会话不存在!"));ctx.close()
    }
  }

  def routeMessage(ctx:ChannelHandlerContext,msg:JtvMessage): Unit ={
    ServerSessionManager.getPairChannel(ctx.channel()).foreach(_.writeAndFlush(msg))
  }
}
