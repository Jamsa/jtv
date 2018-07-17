package com.github.jamsa.jtv.client.gui

import java.awt.FlowLayout
import java.awt.event._
import java.io.ByteArrayInputStream
import java.util.{Observable, Observer}

import com.github.jamsa.jtv.client.manager.{MainFrameManager, RemoteFrameManager}
import com.github.jamsa.jtv.common.model.{ErrorMessage, LoginResponse, ScreenCaptureMessage}
import com.github.jamsa.jtv.common.utils.ImageUtils
import javax.imageio.ImageIO
import javax.swing._

object MainFrame extends JFrame with Observer{

  val sessionId = new JTextField(10)
  val sessionPassword = new JTextField(10)

  val connectBtn = new JButton("申请控制")
  var initFlag = false

  private def initFrame(): Unit ={
    if(initFlag)return
    setTitle("JTV")
    setSize(300,100)
    setAlwaysOnTop(true)
    val contentPanel = getContentPane
    contentPanel.setLayout(new FlowLayout())
    contentPanel.add(new JLabel("会话："))
    contentPanel.add(sessionId)
    sessionId.setEditable(false)
    contentPanel.add(new JLabel("密码："))
    contentPanel.add(sessionPassword)
    sessionPassword.setEditable(false)
    contentPanel.add(connectBtn)

    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)

    MainFrameManager.addObserver(this)


    connectBtn.addActionListener((e: ActionEvent)=>{
      val targetSessionId = JOptionPane.showInputDialog("请输入要控制的会话")
      if(targetSessionId!=null) {
        new RemoteFrame(targetSessionId.toInt, "123").setVisible(true)
      }
    })

    initFlag = true
  }

  initFrame()

  override def update(o: Observable, arg: scala.Any): Unit = {
    arg match {
      case resp:LoginResponse => {
        sessionId.setText(resp.sessionId.toString)
        sessionPassword.setText(resp.sessionPassword)
      }
      case msg:ErrorMessage =>{
        JOptionPane.showMessageDialog(this,msg.message)
      }
      case _ => None
    }
  }
}

class RemoteFrame(targetSessionId:Int,targetSessionPassword:String) extends JFrame with Observer{ frame=>

  val label = new JLabel()
  val manager = new RemoteFrameManager(targetSessionId,targetSessionPassword)
  private def initFrame(): Unit ={
    val contentPanel = getContentPane
    contentPanel.add(label)
    setSize(960,540)

    manager.addObserver(frame)

    addWindowListener(new WindowAdapter {
      override def windowClosing(e: WindowEvent): Unit = {
        super.windowClosing(e)
        manager.stopControl()
        manager.deleteObserver(frame)
      }
    })

    val mouseAdapter = new MouseAdapter {
      override def mouseClicked(e: MouseEvent): Unit = {
        manager.sendEvent(e,frame.getWidth,frame.getHeight)
      }

      override def mousePressed(e: MouseEvent): Unit = {
        manager.sendEvent(e,frame.getWidth,frame.getHeight)
      }

      override def mouseReleased(e: MouseEvent): Unit = {
        manager.sendEvent(e,frame.getWidth,frame.getHeight)
      }

      override def mouseEntered(e: MouseEvent): Unit = {
        manager.sendEvent(e,frame.getWidth,frame.getHeight)
      }

      override def mouseExited(e: MouseEvent): Unit = {
        manager.sendEvent(e,frame.getWidth,frame.getHeight)
      }

      override def mouseWheelMoved(e: MouseWheelEvent): Unit = {
        manager.sendEvent(e,frame.getWidth,frame.getHeight)
      }

      override def mouseDragged(e: MouseEvent): Unit = {
        manager.sendEvent(e,frame.getWidth,frame.getHeight)
      }

      override def mouseMoved(e: MouseEvent): Unit = {
        manager.sendEvent(e,frame.getWidth,frame.getHeight)
      }
    }

    label.addMouseListener(mouseAdapter)
    label.addMouseMotionListener(mouseAdapter)
    label.addMouseWheelListener(mouseAdapter)

    val keyAdapter = new KeyAdapter {
      override def keyTyped(e: KeyEvent): Unit = {
        manager.sendEvent(e,frame.getWidth,frame.getHeight)
      }

      override def keyPressed(e: KeyEvent): Unit = {
        manager.sendEvent(e,frame.getWidth,frame.getHeight)
      }

      override def keyReleased(e: KeyEvent): Unit = {
        manager.sendEvent(e,frame.getWidth,frame.getHeight)
      }
    }

    label.addKeyListener(keyAdapter)
    manager.connect()
    manager.sendControlReq()
  }

  initFrame()

  override def update(o: Observable, arg: scala.Any): Unit = {
    if(!this.isVisible) return
    arg match  {
      case m:ScreenCaptureMessage =>{
        label.setIcon(new ImageIcon(ImageUtils.resizeImage(ImageIO.read(new ByteArrayInputStream(m.image)),960,540)))
      }
      case msg:ErrorMessage =>{
        JOptionPane.showMessageDialog(this,msg.message)
      }
      case _ => None
    }
  }
}