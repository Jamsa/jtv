package com.github.jamsa.jtv.common.utils

import java.awt.Image
import java.awt.image.BufferedImage
import java.io._

import com.github.jamsa.jtv.common.network.Connection
import io.netty.channel.Channel
import io.netty.util.AttributeKey
import javax.imageio.ImageIO
import javax.swing.{Icon, ImageIcon}
import javax.swing.filechooser.FileSystemView

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
    val resized = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB)
    val g2d = resized.createGraphics
    g2d.drawImage(tmp, 0, 0, null)
    g2d.dispose()
    resized
  }

  def toBufferedImage(img:Image): BufferedImage ={
    img match {
      case i:BufferedImage =>i
      case _ =>{
        val bimage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB) //ARGB
        val g2d = bimage.createGraphics
        g2d.drawImage(img, 0, 0, null)
        g2d.dispose()
        bimage
      }
    }
  }

  def toByteArray(img:BufferedImage): Array[Byte] ={
    val bos = new ByteArrayOutputStream()
    ImageIO.write(img,"png",bos)
    return bos.toByteArray
  }

  def toByteArray(img:Image): Array[Byte] ={
    toByteArray(toBufferedImage(img))
  }

  def toBufferedImage(bytes:Array[Byte]): BufferedImage ={
    val bis = new ByteArrayInputStream(bytes)
    ImageIO.read(bis)
  }

  def toImageIcon(bytes:Array[Byte]):ImageIcon ={
    new ImageIcon(toBufferedImage(bytes))
  }

  def toByteArray(icon:ImageIcon):Array[Byte]={
    toByteArray(icon.getImage)
  }

  def getFileIconImage(file:File):BufferedImage ={
    val icon = Option(FileSystemView.getFileSystemView.getSystemIcon(file))
    icon match {
      case Some(i:ImageIcon) => toBufferedImage(i.getImage)
      case Some(i:Icon) => {
        val image = new BufferedImage(i.getIconWidth, i.getIconHeight, BufferedImage.TYPE_INT_ARGB)
        i.paintIcon(null, image.getGraphics, 0, 0)
        image
      }
      case _ =>{
        new BufferedImage(16,16,BufferedImage.TYPE_INT_ARGB)
      }
    }
  }

}


object ChannelUtils{
  private val SESSIONID_KEY = AttributeKey.newInstance[Int]("sessionId")

  private val CONNECTION_KEY = AttributeKey.newInstance[Connection]("connection")

  def getSessionId(channel:Channel)={
    Option(channel.attr(SESSIONID_KEY).get())
  }

  def setSessionId(channel:Channel,sessionId:Int)={
    channel.attr(SESSIONID_KEY).set(sessionId)
  }

  def setConnection(channel:Channel,conn:Connection)={
    channel.attr(CONNECTION_KEY).set(conn)
  }

  def getConnection(channel: Channel) ={
    Option(channel.attr(CONNECTION_KEY).get())
  }
}