package com.github.catvod.api


import com.github.catvod.api.Pan123Api.login
import com.github.catvod.bean.pan123.Cache
import com.github.catvod.bean.pan123.User
import com.github.catvod.crawler.SpiderDebug
import com.github.catvod.spider.Init
import com.github.catvod.utils.Notify
import com.github.catvod.utils.Path
import com.github.catvod.utils.Swings
import com.github.catvod.utils.Util
import org.apache.commons.lang3.StringUtils
import java.awt.event.ActionEvent
import java.awt.event.ActionListener
import java.io.File
import javax.swing.JButton
import javax.swing.JDialog
import javax.swing.JPanel
import javax.swing.JTextField

object Pan123Handler {


    private var cache: Cache? = null
    private var dialog: JDialog? = null

    private var auth = ""
    private var userName = ""
    private var passwd = ""
    private var expire = 0L;

    fun getCache(): File {
        return Path.tv("pan123")
    }

    fun getAuth(): String {
        return auth
    }


    init {

        cache = Cache.objectFrom(Path.read(getCache()))
        if (cache == null) {
            SpiderDebug.log("cache 为null")
            startFlow()
        } else {
            userName = cache!!.user.userName
            passwd = cache!!.user.password
            auth = cache!!.user.cookie
            expire = cache!!.user.expire
            if (StringUtils.isNotBlank(userName) && StringUtils.isNotBlank(passwd)) {
                if (StringUtils.isBlank(auth) || expire == 0L || System.currentTimeMillis() > expire) {
                    SpiderDebug.log("auth 为空或者登录已过期")
                    this.loginWithPassword(userName, passwd)
                }
            } else {
                SpiderDebug.log("userName passwd 为空")
                startFlow()
            }
        }

    }


    fun loginWithPassword(uname: String?, passwd: String?) {
        SpiderDebug.log("loginWithPassword  uname: $uname，passwd：$passwd")

        try {
            //保存的账号密码
            val json = login(uname!!, passwd!!)
            if (json != null) {
                val user = User()
                user.cookie = json.get("token").asString
                user.password = passwd
                user.userName = uname
                user.expire = json.get("refresh_token_expire_time").asLong * 1000
                this.auth = json.get("token").asString
                cache?.setUserInfo(user)
                Notify.show("123登录成功")
            } else {
                Notify.show("123登录失败")
            }

        } catch (e: Exception) {
            SpiderDebug.log("登录失败: " + e.message)
            Notify.show("123登录失败: " + e.message)
        }
    }

    fun startFlow() {
        Init.run { this.showInput() }
    }


    private fun showInput() {
        try {
            val jPanel = JPanel()
            jPanel.setSize(Swings.dp2px(200), Swings.dp2px(80))

            val nameField = JTextField()
            nameField.setName("请输入123用户名")
            nameField.setColumns(Swings.dp2px(38))
            val passwordField = JTextField()
            passwordField.setName("请输入123密码")
            passwordField.setColumns(Swings.dp2px(38))
            val button = JButton("Ok")
            jPanel.add(nameField)
            jPanel.add(passwordField)
            jPanel.add(button)


            val jDialog = Util.showDialog(jPanel, "请输入123用户名和密码")
            button.addActionListener(ActionListener { event: ActionEvent? ->
                onPositive(
                    nameField.getText().toString(), passwordField.getText().toString()
                )
                jDialog.dispose()
            })

        } catch (ignored: Exception) {
        }
    }


    private fun onPositive(username: String?, password: String?) {
        SpiderDebug.log("123用户名: $username")
        SpiderDebug.log("123密码: $password")
        dismiss()
        Init.execute(Runnable {
            loginWithPassword(username, password)
        })
    }

    private fun dismiss() {
        try {
            if (dialog != null) {
                dialog!!.setVisible(false)
                dialog!!.dispose()
            }
        } catch (ignored: java.lang.Exception) {
        }
    }


}