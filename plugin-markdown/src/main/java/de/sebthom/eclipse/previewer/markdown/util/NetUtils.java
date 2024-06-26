/*
 * SPDX-FileCopyrightText: © Sebastian Thomschke and contributors.
 * SPDX-FileContributor: Sebastian Thomschke
 * SPDX-License-Identifier: EPL-2.0
 * SPDX-ArtifactOfProjectHomePage: https://github.com/sebthom/previewer-eclipse-plugin
 */
package de.sebthom.eclipse.previewer.markdown.util;

import java.net.Authenticator;
import java.net.InetSocketAddress;
import java.net.PasswordAuthentication;
import java.net.ProxySelector;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpClient.Redirect;
import java.net.http.HttpClient.Version;
import java.time.Duration;

import org.eclipse.core.net.proxy.IProxyData;
import org.eclipse.core.net.proxy.IProxyService;
import org.eclipse.jdt.annotation.Nullable;

import de.sebthom.eclipse.previewer.markdown.Plugin;

/**
 * @author Sebastian Thomschke
 */
public final class NetUtils {

   /**
    * @param uri for HTTP proxy selection
    */
   public static HttpClient getHttpClient(final URI uri) {
      final var httpClientBuilder = HttpClient.newBuilder();
      final var proxyService = getProxyService();
      if (proxyService != null) {
         for (final IProxyData proxyCfg : proxyService.select(uri)) {
            if (proxyCfg.getType().equals(IProxyData.HTTP_PROXY_TYPE)) {
               httpClientBuilder.proxy(ProxySelector.of(new InetSocketAddress(proxyCfg.getHost(), proxyCfg.getPort())));
               if (proxyCfg.isRequiresAuthentication()) {
                  httpClientBuilder.authenticator(new Authenticator() {
                     @Override
                     protected PasswordAuthentication getPasswordAuthentication() {
                        return new PasswordAuthentication(proxyCfg.getUserId(), proxyCfg.getPassword().toCharArray());
                     }
                  });
               }
               break;
            }
         }
      }

      return httpClientBuilder.version(Version.HTTP_2) //
         .followRedirects(Redirect.NORMAL) //
         .connectTimeout(Duration.ofSeconds(10)) //
         .build();
   }

   private static @Nullable IProxyService getProxyService() {
      if (!Plugin.isInitialized())
         return null;

      final var ctx = Plugin.get().getBundle().getBundleContext();
      if (ctx == null)
         return null;

      final var proxyServiceRef = ctx.getServiceReference(IProxyService.class);
      return proxyServiceRef == null ? null : ctx.getService(proxyServiceRef);
   }

   private NetUtils() {
   }
}
