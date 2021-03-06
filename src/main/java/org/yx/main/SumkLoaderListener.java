/**
 * Copyright (C) 2016 - 2017 youtongluan.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 		http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.yx.main;

import java.io.InputStream;
import java.util.EventListener;
import java.util.List;

import javax.servlet.Servlet;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.ServletRegistration;

import org.yx.bean.IOC;
import org.yx.bean.Loader;
import org.yx.common.StartConstants;
import org.yx.common.StartContext;
import org.yx.conf.AppInfo;
import org.yx.http.ServletInfo;
import org.yx.http.filter.HttpLoginWrapper;
import org.yx.http.filter.LoginServlet;
import org.yx.log.Log;
import org.yx.util.CollectionUtils;

/**
 * 如果使用tomcat等外部容器启动sumk，请在web.xml中添加：<BR>
 * &lt;listener&gt;<br>
 * &nbsp;&nbsp;
 * &lt;listener-class&gt;org.yx.main.SumkLoaderListener&lt;/listener-class&gt;
 * <br>
 * &lt;/listener&gt;
 * 
 * @author 游夏
 *
 */
public class SumkLoaderListener implements ServletContextListener {

	@Override
	public void contextInitialized(ServletContextEvent sce) {
		Log.get("sumk.http").debug("contextInitialized");
		SumkServer.start(new String[] { StartConstants.NOJETTY });
		if (!SumkServer.isHttpEnable()) {
			return;
		}
		ServletContext context = sce.getServletContext();
		injectUserServlets(context);
		@SuppressWarnings("unchecked")
		List<ServletInfo> servlets = (List<ServletInfo>) StartContext.inst.get(ServletInfo.class);
		for (ServletInfo info : servlets) {
			Servlet bean = IOC.get(info.getServletClz());
			ServletRegistration.Dynamic dynamic = bean != null ? context.addServlet(info.getPath(), bean)
					: context.addServlet(info.getPath(), info.getServletClz());
			dynamic.addMapping(info.getPath());

		}

		String path = AppInfo.get("http.login.path", "/login");
		if (IOC.getBeans(LoginServlet.class).size() > 0) {
			String loginPath = (String) path;
			if (!loginPath.startsWith("/")) {
				loginPath = "/" + loginPath;
			}
			Log.get("sumk.http").info("login path:{}", context.getContextPath() + loginPath);
			context.addServlet(loginPath, HttpLoginWrapper.class).addMapping(loginPath);
		}
		addListeners(context);
	}

	/**
	 * @param context
	 */
	private void addListeners(ServletContext context) {
		try {
			InputStream in = Loader.getResourceAsStream("META-INF/http.listeners");
			addListener(context, CollectionUtils.loadList(in));
		} catch (Exception e) {
			Log.printStack(e);
			return;
		}
	}

	private void addListener(ServletContext context, List<String> intfs) throws ClassNotFoundException {
		for (String intf : intfs) {
			Class<?> clz = Class.forName(intf);
			if (!EventListener.class.isAssignableFrom(clz)) {
				Log.get("sumk.http").info(intf + " is not implement EventListener");
				continue;
			}
			@SuppressWarnings("unchecked")
			List<EventListener> listeners = (List<EventListener>) IOC.getBeans(clz);
			if (org.yx.util.CollectionUtils.isEmpty(listeners)) {
				continue;
			}
			listeners.forEach(lis -> {
				Log.get("sumk.http").trace("add web listener:{}", lis.getClass().getSimpleName());
				context.addListener(lis);
			});
		}
	}

	private void injectUserServlets(ServletContext context) {

	}

	@Override
	public void contextDestroyed(ServletContextEvent sce) {
		SumkServer.stop();
	}

}
