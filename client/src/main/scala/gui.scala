package com.github.jamsa.jtv.client.gui


import java.awt._
import java.awt.event._
import java.awt.image.BufferedImage
import java.io.{ByteArrayInputStream, File}
import java.util
import java.util.{Observable, Observer}

import com.github.jamsa.jtv.client.manager.{MainFrameManager, RemoteFrameManager}
import com.github.jamsa.jtv.common.model._
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
    val menuBar = new JMenuBar
    setJMenuBar(menuBar)

    val fileMenu = new JMenu("文件")
    menuBar.add(fileMenu)

    val remoteFileMenuItem = new JMenuItem("文件传输")
    fileMenu.add(remoteFileMenuItem)
    remoteFileMenuItem.addActionListener(e=>{
      val fileFrame = new RemoteFileFrame(targetSessionId,manager)
      fileFrame.setVisible(true)
    })

    val parseControlMenuItem = new JMenuItem("暂停控制")
    fileMenu.add(parseControlMenuItem)
    parseControlMenuItem.addActionListener(e=>{
      manager.toggleControl()
      val text = parseControlMenuItem.getText() match {
        case "暂停控制" => "恢复控制"
        case _ => "暂停控制"
      }
      parseControlMenuItem.setText(text)
    })

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

class RemoteFileFrame(val targetSessionId:Int,val manager:RemoteFrameManager) extends JFrame with Observer{frame =>
  val llabel = new JLabel("本地")
  val rlabel = new JLabel("远程")
  val lpath = new JTextField()
  val rpath = new JTextField()
  val ltoolbar = new JLabel("本地工具")
  val rtoolbar = new JLabel("远程工具")
  val llist = new JList[FileInfo]()
  llist.setCellRenderer(new FileRender)
  llist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
  val rlist = new JList[FileInfo]()
  rlist.setCellRenderer(new FileRender)
  rlist.setSelectionMode(ListSelectionModel.SINGLE_SELECTION)
  val lsview = new JScrollPane()
  lsview.setViewportView(llist)
  val rsview =new JScrollPane()
  rsview.setViewportView(rlist)

  val transferToolbar = new JToolBar(null, SwingConstants.VERTICAL)
  transferToolbar.setFloatable(false)
  val ltorBtn = new JButton(">")
  val rtolBtn = new JButton("<")
  transferToolbar.add(ltorBtn)
  transferToolbar.add(rtolBtn)

  def initFrame(): Unit ={
    setTitle("文件传输")
    setSize(600,500)

    manager.addObserver(frame)

    addWindowListener(new WindowAdapter {
      override def windowClosing(e: WindowEvent): Unit = {
        super.windowClosing(e)
        manager.deleteObserver(frame)
      }
    })

    val layout = new GridBagLayout()
    val panel = getContentPane
    panel.setLayout(layout)

    panel.add(llabel)
    panel.add(new JLabel())
    panel.add(rlabel)
    panel.add(ltoolbar)
    panel.add(new JLabel())
    panel.add(rtoolbar)
    panel.add(lpath)
    panel.add(new JLabel())
    panel.add(rpath)
    panel.add(lsview)
    panel.add(transferToolbar)
    panel.add(rsview)

    llist.addMouseListener(new MouseAdapter {
      override def mouseClicked(e: MouseEvent): Unit = {
        if(e.getClickCount==2){
          val fileInfo = llist.getModel.getElementAt(llist.getSelectedIndex)
          if(fileInfo.file.isDirectory){
            llist.setListData(manager.listFile(fileInfo.file))
            lpath.setText(fileInfo.file.getAbsolutePath)
          }
        }
      }
    })

    rlist.addMouseListener(new MouseAdapter {
      override def mouseClicked(e: MouseEvent): Unit = {
        if(e.getClickCount==2){
          val fileInfo = rlist.getModel.getElementAt(rlist.getSelectedIndex)
          if(fileInfo.file.isDirectory){
            manager.sendFileListRequest(FileListRequest(fileInfo.file))
          }
        }
      }
    })

    lpath.addKeyListener(new KeyAdapter {
      override def keyReleased(e: KeyEvent): Unit = {
        if(e.getKeyCode!=KeyEvent.VK_ENTER) return
        val f = new File(lpath.getText)
        if(f.exists() ){
          val directory = if(f.isDirectory) f else f.getParentFile
          llist.setListData(manager.listFile(directory))
        }
      }
    })

    rpath.addKeyListener(new KeyAdapter {
      override def keyPressed(e: KeyEvent): Unit = {
        if(e.getKeyCode!=KeyEvent.VK_ENTER) return
        val f = new File(rpath.getText)
        manager.sendFileListRequest(FileListRequest(f))
      }
    })

    ltorBtn.addActionListener(e=>{
      val lfile = FileInfo(llist.getSelectedValue.file, Array[Byte](0))
      val rfile = FileInfo(new File(rpath.getText), Array[Byte](0))
      if(llist.getSelectedValue.file.isFile) {
        manager.tranferFile(FileTransferRequestType.PUT, lfile,rfile)
      } else {
        JOptionPane.showMessageDialog(this,"暂不支持目录传输")
      }
    })

    rtolBtn.addActionListener(e=>{
      val rfile = rlist.getSelectedValue
      val lfile = FileInfo(new File(lpath.getText),Array[Byte](0))
      manager.tranferFile(FileTransferRequestType.GET,rfile,lfile)
    })


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
    s.insets = new Insets(0,10,0,0) //左边留白
    layout.setConstraints(llabel, s)

    s.gridwidth=0
    s.insets = new Insets(0,0,0,10) //右边留白
    layout.setConstraints(rlabel,s)


    s.gridwidth=1
    s.insets = new Insets(0,10,0,0)
    layout.setConstraints(ltoolbar,s)

    s.gridwidth=0
    s.insets = new Insets(0,0,0,10)
    layout.setConstraints(rtoolbar,s)

    s.gridwidth=1
    s.weightx=1
    s.insets = new Insets(0,10,0,0)
    layout.setConstraints(lpath,s)

    s.gridwidth=0
    s.weightx=1
    s.insets = new Insets(0,0,0,10)
    layout.setConstraints(rpath,s)


    s.gridwidth=1
    s.weightx=1
    s.weighty=1
    s.insets = new Insets(0,10,0,0)
    layout.setConstraints(lsview,s)



    s.gridwidth=0
    s.weightx=1
    s.weighty=1
    s.insets = new Insets(0,0,0,10)
    layout.setConstraints(rsview,s)

    manager.sendFileListRequest(FileListRequest(new File("/")))
    llist.setListData(manager.listFile(new File("/")))
    lpath.setText("/")
    //llist.setSelectedIndex(0)
  }

  initFrame()


  override def update(o: Observable, arg: scala.Any): Unit = {
    arg match  {
      case m:FileListResponse =>{
        rlist.setListData(m.files)
        rpath.setText(m.directory.getAbsolutePath)
      }
      case _ => None
    }
  }

  //继承Java泛型类引起的问题
  //https://www.scala-lang.org/old/node/10687
  //https://stackoverflow.com/questions/7222161/jlist-that-contains-the-list-of-files-in-a-directory
  //https://stackoverflow.com/questions/6440176/scala-overriding-generic-java-methods-ii
  /*class FileRender extends DefaultListCellRenderer{
    this:ListCellRenderer[scala.Any] =>
    override def getListCellRendererComponent(list: JList[_<:scala.Any], value: scala.Any, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus)

  }*/
  /*override def getListCellRendererComponent(list: JList[FileInfo], value: AnyRef, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
      val label = new JLabel()
      label.setFocusable(true)
      val fileInfo = value
      //label.setText(fileInfo.file.getName)
      //label.setIcon(ImageUtils.toImageIcon(fileInfo.icon))
      label
    }*/

  class FileRender extends ListCellRenderer[FileInfo]{
    val render = (new DefaultListCellRenderer).asInstanceOf[ListCellRenderer[FileInfo]]

    override def getListCellRendererComponent(list: JList[_ <: FileInfo], value: FileInfo, index: Int, isSelected: Boolean, cellHasFocus: Boolean): Component = {
      val result = render.getListCellRendererComponent(list,value,index,isSelected,cellHasFocus)
      val label = result.asInstanceOf[JLabel]
      label.setText(value.file.getName)
      label.setIcon(ImageUtils.toImageIcon(value.icon))
      label
    }
  }

}