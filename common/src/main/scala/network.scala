package com.github.jamsa.jtv.common.network

import com.github.jamsa.jtv.common.model.JtvMessage
import com.github.jamsa.jtv.common.utils.ChannelUtils
import io.netty.channel.{Channel, ChannelFuture, ChannelHandlerContext}

trait ConnectionCallback{
  def onClose(future:ChannelFuture)
  def onMessage(ctx:ChannelHandlerContext,message:JtvMessage)
}

object ConnectionCallbackAdapter extends ConnectionCallback {
  override def onClose(future: ChannelFuture): Unit = {}
  override def onMessage(ctx: ChannelHandlerContext, message: JtvMessage): Unit = {}
}

class Connection(val channel:Channel,val callback:ConnectionCallback){conn=>
  channel.closeFuture().addListener((future:ChannelFuture) =>{
    callback.onClose(future)
  })

  ChannelUtils.setConnection(channel,this)

  def sendMessage(message:JtvMessage)={
    channel.writeAndFlush(message)
  }

  def close(): ChannelFuture ={
    channel.close()
  }
}
