/**
 * 处理用户建立的tcp连接
 */
package jrp.server.handler;

import java.io.IOException;
import java.net.Socket;
import java.util.concurrent.TimeUnit;

import jrp.log.Logger;
import jrp.server.Context;
import jrp.server.Message;
import jrp.server.model.OuterLink;
import jrp.server.model.TunnelInfo;
import jrp.socket.SocketHelper;

public class TcpHandler implements Runnable
{
	private Socket socket;
	private Context context;
	private Logger log;

	public TcpHandler(Socket socket, Context context)
	{
		this.socket = socket;
		this.context = context;
		this.log = context.log;
	}

	@Override
	public void run()
	{
		log.log("收到外部请求");
		try(Socket socket = this.socket)
		{
			int remotePort = socket.getLocalPort();
			TunnelInfo tunnel = context.getTunnelInfo(remotePort);
			if(tunnel != null)
			{
				OuterLink outerLink = new OuterLink();
				outerLink.setRemotePort(remotePort);
				outerLink.setOuterSocket(socket);
				outerLink.setControlSocket(tunnel.getControlSocket());
				try
				{
					// 捕获可能因网络断开而产生的异常
					SocketHelper.sendpack(tunnel.getControlSocket(), Message.ReqProxy());
				}
				catch(IOException e)
				{
					tunnel.getControlSocket().close();
					return;
				}
				context.offerOuterLink(tunnel.getClientId(), outerLink);
				try(Socket proxySocket = outerLink.pollProxySocket(60, TimeUnit.SECONDS))// 最多等待60秒
				{
					if(proxySocket != null)
					{
						SocketHelper.forward(socket, proxySocket);
					}
				}
				catch(Exception e)
				{
				}
			}
		}
		catch(Exception e)
		{
			log.err(e.toString());
		}
	}
}