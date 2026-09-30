package com.nezurstandalone.utils;
import com.google.gson.JsonObject;
import java.io.*;
import java.util.*;
import java.util.zip.*;
/** One complete, CRC-checked GZIP member; bounded before allocation and inflation. */
public final class PresetCode {
    public static final int MAX_COMPRESSED=262144;
    private PresetCode(){}
    public static JsonObject decode(String code)throws IOException {
        if(code==null || code.length()>((MAX_COMPRESSED+2)/3)*4+16)throw new IOException("Preset code size");
        String encoded=code.trim();if(encoded.startsWith("Nezur-"))encoded=encoded.substring(6);else if(encoded.startsWith("PitLite-"))encoded=encoded.substring(8);
        byte[] data;try{data=Base64.getDecoder().decode(encoded);}catch(IllegalArgumentException ex){throw new IOException("Invalid preset base64",ex);}
        if(!Base64.getEncoder().withoutPadding().encodeToString(data).equals(encoded.replaceFirst("=+$","")))throw new IOException("Non-canonical preset base64");
        if(data.length<18 || data.length>MAX_COMPRESSED || u(data,0)!=31 || u(data,1)!=139 || u(data,2)!=8)throw new IOException("Invalid GZIP header");
        int flags=u(data,3),pos=10;if((flags&224)!=0)throw new IOException("Reserved GZIP flags");
        if((flags&4)!=0){int len=u(data,pos)|(u(data,pos+1)<<8);pos+=2;pos+=len;if(pos>data.length)throw new IOException("Truncated GZIP extra");}
        if((flags&8)!=0){while(u(data,pos++)!=0){}}
        if((flags&16)!=0){while(u(data,pos++)!=0){}}
        if((flags&2)!=0){CRC32 header=new CRC32();header.update(data,0,pos);int expected=u(data,pos)|(u(data,pos+1)<<8);if((header.getValue()&65535)!=expected)throw new IOException("GZIP header CRC");pos+=2;}
        Inflater inflater=new Inflater(true);ByteArrayOutputStream out=new ByteArrayOutputStream();CRC32 crc=new CRC32();
        try{
            if(pos>data.length-8)throw new IOException("Truncated GZIP body");inflater.setInput(data,pos,data.length-pos);byte[] buffer=new byte[4096];
            while(!inflater.finished()){
                int n=inflater.inflate(buffer);
                if(n>0){if(out.size()+n>BoundedJson.MAX_BYTES)throw new IOException("Expanded preset too large");out.write(buffer,0,n);crc.update(buffer,0,n);}
                else if(!inflater.finished())throw new IOException("Truncated/unsupported GZIP stream");
            }
            int trailer=data.length-inflater.getRemaining();
            if(trailer+8!=data.length || le32(data,trailer)!=crc.getValue() || le32(data,trailer+4)!=out.size())throw new IOException("Invalid GZIP trailer or trailing data");
            return BoundedJson.object(out.toByteArray());
        }catch(DataFormatException e){throw new IOException("Invalid compressed preset",e);}finally{inflater.end();}
    }
    private static int u(byte[] b,int i)throws IOException{if(i<0 || i>=b.length)throw new IOException("Truncated GZIP header");return b[i]&255;}
    private static long le32(byte[] b,int i)throws IOException{return (long)u(b,i)|((long)u(b,i+1)<<8)|((long)u(b,i+2)<<16)|((long)u(b,i+3)<<24);}
}
