package tech.nmfin.algoquant.web;
import java.io.*;import java.net.*;import java.nio.charset.StandardCharsets;import java.util.*;
/** Anonymous, bounded HTTPS CSV loading; redirects and destinations are checked before each request. */
public final class PublicCsvSource {
 public static String canonical(String input) {
  try{
   URI u=new URI(input.trim());
   if(!"https".equalsIgnoreCase(u.getScheme())||u.getHost()==null||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))
    throw new IllegalArgumentException("Use a public HTTPS CSV or Google Sheets URL without embedded credentials.");
   if("docs.google.com".equalsIgnoreCase(u.getHost())&&u.getPath().startsWith("/spreadsheets/d/")){
    String id=u.getPath().substring("/spreadsheets/d/".length()).split("/")[0];
    if(!id.matches("[A-Za-z0-9_-]+"))throw new IllegalArgumentException("Invalid Google Sheet URL.");
    String gid="0";for(String part:((u.getRawQuery()==null?"":u.getRawQuery())+"&"+(u.getRawFragment()==null?"":u.getRawFragment())).split("&"))
     if(part.startsWith("gid=")){gid=part.substring(4);if(!gid.matches("[0-9]+"))throw new IllegalArgumentException("Invalid sheet tab ID.");}
    return "https://docs.google.com/spreadsheets/d/"+id+"/export?format=csv&gid="+gid;
   }
   String raw=u.toASCIIString();int fragment=raw.indexOf('#');return fragment<0?raw:raw.substring(0,fragment);
  }catch(URISyntaxException ex){throw new IllegalArgumentException("Enter a valid public HTTPS URL.");}
 }
 public static void validateDestination(URI u)throws IOException {
  String host=u.getHost();if(host==null||!"https".equalsIgnoreCase(u.getScheme())||u.getUserInfo()!=null||(u.getPort()!=-1&&u.getPort()!=443))
   throw new IllegalArgumentException("Only public HTTPS destinations are supported.");
  for(InetAddress address:InetAddress.getAllByName(host)){
   byte[] bytes=address.getAddress();
   boolean carrier=bytes.length==4&&(bytes[0]&255)==100&&((bytes[1]&255)>=64&&(bytes[1]&255)<=127);
   boolean unique=bytes.length==16&&((bytes[0]&254)==252);
   if(address.isAnyLocalAddress()||address.isLoopbackAddress()||address.isSiteLocalAddress()||address.isLinkLocalAddress()||address.isMulticastAddress()||carrier||unique)
    throw new IllegalArgumentException("The URL must point to a public Internet address.");
  }
 }
 public String load(String input)throws IOException {
  URI url=URI.create(canonical(input));long deadline=System.currentTimeMillis()+60000;
  for(int redirect=0;redirect<5;redirect++){
   if(System.currentTimeMillis()>deadline)throw new IOException("CSV download timed out.");
   validateDestination(url);HttpURLConnection c=(HttpURLConnection)url.toURL().openConnection();
   c.setConnectTimeout(10000);c.setReadTimeout(15000);c.setInstanceFollowRedirects(false);
   c.setRequestProperty("Accept","text/csv,text/plain;q=0.9");c.setRequestProperty("User-Agent","NMFinTech-PerformanceAnalyzer/1.0");
   try{
    int status=c.getResponseCode();
    if(status>=300&&status<400){String next=c.getHeaderField("Location");if(next==null)throw new IOException("CSV redirect has no location.");url=url.resolve(next);continue;}
    if(status!=200)throw new IOException("Public CSV download returned HTTP "+status+". Make the sheet publicly readable without signing in.");
    String type=c.getContentType();if(type!=null&&type.toLowerCase(Locale.ROOT).contains("text/html"))throw new IllegalArgumentException("The URL returned a webpage. Supply a direct CSV URL or a publicly readable Google Sheet.");
    try(InputStream in=c.getInputStream();ByteArrayOutputStream out=new ByteArrayOutputStream()){
     byte[] b=new byte[8192];int n;
     while((n=in.read(b))!=-1){if(out.size()+n>2*1024*1024)throw new IllegalArgumentException("Input CSV exceeds the 2 MB limit.");if(System.currentTimeMillis()>deadline)throw new IOException("CSV download timed out.");out.write(b,0,n);}
     return new String(out.toByteArray(),StandardCharsets.UTF_8);
    }
   }finally{c.disconnect();}
  }throw new IOException("Too many CSV redirects.");
 }
}
