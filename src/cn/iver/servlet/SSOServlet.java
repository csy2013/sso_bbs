package cn.iver.servlet;

import cn.iver.common.Const;
import cn.iver.model.User;
import org.eclipse.jetty.server.Authentication;
import org.ofbiz.sso.cas.ssl.client.HttpsURLConnectionFactory;
import org.ofbiz.sso.cas.ssl.client.WhitelistHostnameVerifier;
import org.ofbiz.sso.cas.ssl.client.util.CommonUtils;
import org.ofbiz.sso.util.UtilXml;
import org.ofbiz.sso.util.XmlUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Element;

import javax.servlet.ServletException;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.*;
import java.net.URL;
import java.net.URLEncoder;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Created by changshengyong on 15/6/14.
 */
public class SSOServlet extends HttpServlet {

    public static final String PARAM_TICKET = "ticket";

    public static final String PARAM_SERVICE = "service";

    public static final String PARAM_RENEW = "renew";

    private static final String ssoConfig = "sso.xml";



    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)  {
        Element xml = getRootElement(req);
        String requestURI = req.getRequestURI();
        try {
            if(requestURI.endsWith("login")) {
                User user = login(req, resp, xml);
                if (user != null) {
                    if (user.get("id").equals("-1")) {
                        return;
                    } else
                        resp.sendRedirect(req.getContextPath() + "/user/login?email=" + user.get("email") + "&password=" + user.get("password"));
                } else {
                    resp.sendRedirect(req.getContextPath() + "/user/login");
                }
            }else if(requestURI.endsWith("logout")){
                logout(req,resp,xml);
            }
        } catch (Exception e) {
//            e.printStackTrace();
            System.out.println("e.getMessage() = " + e.getMessage());
        }

    }


    public User login(HttpServletRequest request, HttpServletResponse response, Element rootElement) throws Exception {
        User user = new User();
        user.set("id","-1");
        String ticket = request.getParameter(PARAM_TICKET);
        String username = request.getParameter("USERNAME");
        String password = request.getParameter("PASSWORD");

        String casUrl = UtilXml.childElementValue(rootElement, "CasUrl", "https://localhost:8443/cas");
        String loginUri = UtilXml.childElementValue(rootElement, "CasLoginUri", "/login");
        String validateUri = UtilXml.childElementValue(rootElement, "CasValidateUri", "/validate");
        String whiteHosts = UtilXml.childElementValue(rootElement,"allowHosts","localhost");

        String serviceName = UtilXml.childElementValue(rootElement, "ServiceName", "www.yuaoq.com");

        String requestURI = request.getRequestURI();
        String url =  request.getScheme()+"://"+ serviceName+requestURI;
//        String serviceUrl = request.getRequestURL().toString();
         url = URLEncoder.encode(url, "UTF-8");
        boolean casLoggedIn = false;
        if (ticket == null) {
            // forward the login page to CAS login page
            response.sendRedirect(casUrl + loginUri + "?" + PARAM_SERVICE + "=" + url);


        } else {
            // there's a ticket, we should validate the ticket
            URL validateURL = new URL(casUrl + validateUri + "?" + PARAM_TICKET + "=" + ticket + "&" + PARAM_SERVICE + "=" + url);

            HttpsURLConnectionFactory httpsURLConnectionFactory = new HttpsURLConnectionFactory();

            httpsURLConnectionFactory.setHostnameVerifier(new WhitelistHostnameVerifier(whiteHosts));
            httpsURLConnectionFactory.setSSLConfiguration(getSSLConfig());
            httpsURLConnectionFactory.ignorSsl();// 此处处理信任所有的SSL证书
//            SSLUtil.ignoreSsl();
            String responseStr = CommonUtils.getResponseFromServer(validateURL, httpsURLConnectionFactory, "UTF-8");

            if(validateUri.equals("/validate")) {
                BufferedReader reader = new BufferedReader(new StringReader(responseStr));
                String result = reader.readLine();
                if (result != null && result.equals("yes")) {
                    username = reader.readLine();
                    casLoggedIn = true;
                } else {
                    response.sendRedirect(casUrl + loginUri + "?service=" + url);
                }
            }else  if(validateUri.equals("/p3/serviceValidate")){
                final String error = XmlUtils.getTextForElement(responseStr, "authenticationFailure");

                if (CommonUtils.isNotBlank(error)) {
                    return null;
                }
                final String principal = XmlUtils.getTextForElement(responseStr, "user");
                username = principal;
                casLoggedIn = true;
                //String partyId = XmlUtils.getTextForElements(responseStr, "partyId").get(0);
                String password1 = XmlUtils.getTextForElements(responseStr, "password").get(0);
                List emails = XmlUtils.getTextForElements(responseStr, "email");
                String email = "";
                if(emails!=null && (!emails.isEmpty())) {
                     email = XmlUtils.getTextForElements(responseStr, "email").get(0);
                }


                if (casLoggedIn && username != null) {
                    user = User.dao.getByEmailAndPassword(email,password1);
                    if(user==null){
                        user = new User();
                        user.set("password",password1);
                        user.set("email",email);
                        user.set("username",email);
                        user.mySaveNoMD5();
                    }
                    return user;
                }


            }
        }


        return null;
    }


    protected  Element getRootElement(HttpServletRequest request) {

        InputStream configFileIS = null;
        Element rootElement = null;
        try {
//            configFileIS = new FileInputStream(configFile);
            configFileIS =  this.getClass().getClassLoader().getResourceAsStream(ssoConfig);
            Document configDoc = UtilXml.readXmlDocument(configFileIS, "LDAP configuration file " + ssoConfig);
            rootElement = configDoc.getDocumentElement();
        } catch (Exception e) {

        } finally {
            if (configFileIS != null) {
                try {
                    configFileIS.close();
                } catch (IOException e) {
                }
            }
        }

        return rootElement;
    }


    protected Properties getSSLConfig(){
        Properties properties = new Properties();
        InputStream configFileIS = null;

        Element rootElement = null;
        try {

            configFileIS =  this.getClass().getClassLoader().getResourceAsStream(ssoConfig);
            Document configDoc = UtilXml.readXmlDocument(configFileIS, "LDAP configuration file " + ssoConfig);
            rootElement = configDoc.getDocumentElement();
            String keyStorePath = UtilXml.childElementValue(rootElement, "keyStorePath", "");
            String keyStroPass = UtilXml.childElementValue(rootElement, "keyStorePass", "changeit");
            String certificate = UtilXml.childElementValue(rootElement, "certificatePassword", "changeit");
            String keyStoreType = UtilXml.childElementValue(rootElement,"keyStoreType","JKS");
            properties.put("keyStorePath",keyStorePath);
            properties.put("keyStorePass",keyStroPass);
            properties.put("certificatePassword",certificate);
            properties.put("keyStoreType",keyStoreType);

        } catch (Exception e) {

        } finally {
            if (configFileIS != null) {
                try {
                    configFileIS.close();
                } catch (IOException e) {
                }
            }
        }
        return properties;
    }


    public String logout(HttpServletRequest request, HttpServletResponse response, Element rootElement) {

        //先bbs logout
        request.getSession().removeAttribute("user");
        request.getSession().removeAttribute("userID");
        setCookie("bbsID", null, 0, "/", null,response);
        String casUrl = UtilXml.childElementValue(rootElement, "CasUrl", "https://localhost:8443/cas");
        String logoutUri = UtilXml.childElementValue(rootElement, "CasLogoutUri", "/logout");
        String serviceUrl = UtilXml.childElementValue(rootElement, "ser", "http://wxin.club/bbs");
        String serviceName = UtilXml.childElementValue(rootElement, "ServiceName", "www.yuaoq.com");
        String requestURI = request.getRequestURI();
        String url =  request.getScheme()+"://"+ serviceName+requestURI;
        try {
//            response.sendRedirect(casUrl + logoutUri);
            response.sendRedirect(casUrl + logoutUri + "?" + PARAM_SERVICE + "="+url);
        } catch (UnsupportedEncodingException e) {
        } catch (IOException e) {
        }

        return "success";
    }


    public void setCookie(String name, String value, int maxAgeInSeconds, String path, String domain,HttpServletResponse response) {
        Cookie cookie = new Cookie(name, value);
        if (domain != null)
            cookie.setDomain(domain);
        cookie.setMaxAge(maxAgeInSeconds);
        cookie.setPath(path);
        response.addCookie(cookie);

    }

}
