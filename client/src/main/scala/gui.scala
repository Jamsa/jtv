package com.github.jamsa.jtv.client.gui

import java.awt._
import java.awt.event._
import java.awt.image.BufferedImage
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
    setLocationRelativeTo(null)
    //setAlwaysOnTop(true)
    val contentPanel = getContentPane
    val layout = new GridBagLayout

    //contentPanel.setLayout(new FlowLayout())
    contentPanel.setLayout(layout)
    val l1 = new JLabel("会话：")
    contentPanel.add(l1)
    contentPanel.add(sessionId)
    sessionId.setEditable(false)
    val l2 = new JLabel("密码：")
    contentPanel.add(l2)
    contentPanel.add(sessionPassword)
    sessionPassword.setEditable(false)
    contentPanel.add(connectBtn)

    setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE)

    //元素布局
    val s = new GridBagConstraints
    s.fill = GridBagConstraints.BOTH
    //该方法是为了设置如果组件所在的区域比组件本身要大时的显示情况
    //NONE：不调整组件大小。
    //HORIZONTAL：加宽组件，使它在水平方向上填满其显示区域，但是不改变高度。
    //VERTICALss：加高组件，使它在垂直方向上填满其显示区域，但是不改变宽度。
    //BOTH：使组件完全填满其显示区域。
    s.gridwidth=1 //该方法是设置组件水平所占用的格子数，如果为0，就说明该组件是该行的最后一个
    s.weightx = 0 //该方法设置组件水平的拉伸幅度，如果为0就说明不拉伸，不为0就随着窗口增大进行拉伸，0到1之间
    s.weighty=0 //该方法设置组件垂直的拉伸幅度，如果为0就说明不拉伸，不为0就随着窗口增大进行拉伸，0到1之间

    s.insets = new Insets(0,10,0,0)
    s.anchor = GridBagConstraints.EAST
    layout.setConstraints(l1, s) //设置组件

    s.anchor = GridBagConstraints.WEST
    s.insets = new Insets(0,0,0,10)
    s.gridwidth=0
    s.weightx=1
    layout.setConstraints(sessionId,s)

    s.insets = new Insets(0,10,0,0)
    s.anchor = GridBagConstraints.EAST
    s.gridwidth=1
    s.weightx=0
    layout.setConstraints(l2,s)

    s.anchor = GridBagConstraints.WEST
    s.insets = new Insets(0,0,0,10)
    s.gridwidth=0
    s.weightx=1
    layout.setConstraints(sessionPassword,s)

    s.insets = new Insets(0,10,0,10)
    layout.setConstraints(connectBtn,s)

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

class RemoteDesktopPanel extends JPanel {

  private var image:Option[BufferedImage] = None
    private var changed = true

  setBackground(Color.GRAY)
  this.setFocusable(true)

  def setImage(image: BufferedImage): Unit = { //this.image = image;
    this.image = Some(image)
    this.changed=true
    this.repaint()
  }

  override def getPreferredSize: Dimension = {
    super.getPreferredSize
  }

  override def paintComponent(g: Graphics): Unit = {
    super.paintComponent(g)
    if(changed && image.isDefined) {
      val g2d = g.asInstanceOf[Graphics2D]
      g2d.drawImage(image.get,0,0,getWidth,getHeight,this)
      changed=false
    }
  }

}

class RemoteFrame(targetSessionId:Int,targetSessionPassword:String) extends JFrame with Observer{ frame=>

  val canvasPanel = new RemoteDesktopPanel()//new JLabel()
  val manager = new RemoteFrameManager(targetSessionId,targetSessionPassword)
  private def initFrame(): Unit ={
    setContentPane(canvasPanel)
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
        manager.sendEvent(e,canvasPanel.getWidth,canvasPanel.getHeight)
      }

      override def mousePressed(e: MouseEvent): Unit = {
        manager.sendEvent(e,canvasPanel.getWidth,canvasPanel.getHeight)
      }

      override def mouseReleased(e: MouseEvent): Unit = {
        manager.sendEvent(e,canvasPanel.getWidth,canvasPanel.getHeight)
      }

      override def mouseEntered(e: MouseEvent): Unit = {
        manager.sendEvent(e,canvasPanel.getWidth,canvasPanel.getHeight)
      }

      override def mouseExited(e: MouseEvent): Unit = {
        manager.sendEvent(e,canvasPanel.getWidth,canvasPanel.getHeight)
      }

      override def mouseWheelMoved(e: MouseWheelEvent): Unit = {
        manager.sendEvent(e,canvasPanel.getWidth,canvasPanel.getHeight)
      }

      override def mouseDragged(e: MouseEvent): Unit = {
        manager.sendEvent(e,canvasPanel.getWidth,canvasPanel.getHeight)
      }

      override def mouseMoved(e: MouseEvent): Unit = {
        manager.sendEvent(e,canvasPanel.getWidth,canvasPanel.getHeight)
      }
    }

    canvasPanel.addMouseListener(mouseAdapter)
    canvasPanel.addMouseMotionListener(mouseAdapter)
    canvasPanel.addMouseWheelListener(mouseAdapter)

    val keyAdapter = new KeyAdapter {
      override def keyTyped(e: KeyEvent): Unit = {
        manager.sendEvent(e,canvasPanel.getWidth,canvasPanel.getHeight)
      }

      override def keyPressed(e: KeyEvent): Unit = {
        manager.sendEvent(e,canvasPanel.getWidth,canvasPanel.getHeight)
      }

      override def keyReleased(e: KeyEvent): Unit = {
        manager.sendEvent(e,canvasPanel.getWidth,canvasPanel.getHeight)
      }
    }

    canvasPanel.addKeyListener(keyAdapter)
    manager.connect()
    manager.sendControlReq()
  }

  initFrame()

  override def update(o: Observable, arg: scala.Any): Unit = {
    if(!this.isVisible) return
    arg match  {
      case m:ScreenCaptureMessage =>{
        //label.setIcon(new ImageIcon(ImageUtils.resizeImage(ImageIO.read(new ByteArrayInputStream(m.image)),960,540)))
        canvasPanel.setImage(ImageIO.read(new ByteArrayInputStream(m.image)))
      }
      case msg:ErrorMessage =>{
        JOptionPane.showMessageDialog(this,msg.message)
      }
      case _ => None
    }
  }
}