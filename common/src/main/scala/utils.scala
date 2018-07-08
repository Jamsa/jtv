package com.github.jamsa.jtv.common.utils

import java.awt.{Graphics2D, Image}
import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, ByteArrayOutputStream, ObjectInputStream, ObjectOutputStream}

import io.netty.channel.Channel
import io.netty.util.AttributeKey

object CodecUtils{
  def encode(obj:AnyRef):Array[Byte]={
    val out = new ByteArrayOutputStream()
    val oos = new ObjectOutputStream(out)
    oos.writeObject(obj)
    oos.close()
    val result = out.toByteArray
    out.close()
    result
  }

  def decode(bytes:Array[Byte]):AnyRef={
    val in = new ByteArrayInputStream(bytes)
    val ois = new ObjectInputStream(in)
    val result = ois.readObject()
    ois.close()
    in.close()
    result
  }
}

object ImageUtils{
  def resizeImage(bufferedImage: BufferedImage,width:Int,height:Int): BufferedImage ={
    val tmp = bufferedImage.getScaledInstance(width, height, Image.SCALE_SMOOTH)
    val resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB)
    val g2d = resized.createGraphics
    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()
    resized
  }
}


object ChannelUtils{
  val SESSIONID_KEY = AttributeKey.newInstance[Int]("sessionId")

  def getSessionId(channel:Channel)={
    Option(channel.attr(SESSIONID_KEY).get())
  }

  def setSessionId(channel:Channel,sessionId:Int)={
    channel.attr(SESSIONID_KEY).set(sessionId)
  }

}