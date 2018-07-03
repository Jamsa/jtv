import java.awt.event.{InputEvent, KeyEvent}

object RobotTest{
  def main(args: Array[String]): Unit = {
    // 创建一个机器人对象

    val robot = new java.awt.Robot();

    //当前屏幕大小

    val tk = java.awt.Toolkit.getDefaultToolkit();

    val dm = tk.getScreenSize();

    //计算屏幕中心点

    val x = dm.getWidth() / 2 toInt

    val y = dm.getHeight() / 2 toInt

    // 将鼠标移动到屏幕中心

    robot.mouseMove(x, y)

    // 按下鼠标左键

    robot.mousePress(InputEvent.BUTTON1_MASK);

    // 松开鼠标左键

    robot.mouseRelease(InputEvent.BUTTON1_MASK);

    // 模拟按下回车键

    robot.keyPress(KeyEvent.VK_ENTER)

    robot.keyPress(KeyEvent.VK_0);

    // 模拟放松回车键

    robot.keyRelease(KeyEvent.VK_ENTER);

    // 按下SHIFT键

    robot.keyPress(KeyEvent.VK_SHIFT);


  }
}