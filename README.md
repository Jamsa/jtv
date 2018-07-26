本项目是一个学习项目，目标是使用Scala + Netty实现类似远程桌面功能，目前已经实现基本的远程控制功能。

开发笔记在我的[Github Blog](http://jamsa.github.io/jtvkai-fa-bi-ji-1-kai-shi.html)

 - [准备](http://jamsa.github.io/jtvkai-fa-bi-ji-1-kai-shi.html)
 - [网络通讯](http://jamsa.github.io/jtvkai-fa-bi-ji-2-wang-luo-tong-xun.html)
 - [服务端](http://jamsa.github.io/jtvkai-fa-bi-ji-3-fu-wu-duan.html)
 - [客户端](http://jamsa.github.io/jtvkai-fa-bi-ji-4-ke-hu-duan.html)

版本：
    scala 2.12.6
    sbt 1.1.6
    netty 4.1.25.Final

模块划分：

 - client 客户端工程：最终用户运行的 GUI 程序
 
 - common 公用模块：含通讯协议使用的编码/解码器、消息对象、工具类
 
 - server 中心服务程序：管理网络连接和会话，处理消息数据的路由

更新历史：

 - 2018.7.25增加文件传输功能
 
 - 2018.7.17对client模块进行重构。将远程窗口相关的manager对象从主manager对象中分离，以便同时打开多个远程控制窗口。修复键盘事件无法传输的bug。

 - 2018.7.11完成基本的远程桌面功能，实现了屏幕图像、鼠标、键盘事件的传输

