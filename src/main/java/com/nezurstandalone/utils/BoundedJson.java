package com.nezurstandalone.utils;
import com.google.gson.*;
import com.google.gson.stream.*;
import java.io.*;
import java.nio.*;
import java.nio.charset.*;
/** Strict, duplicate-free, bounded config parser; never repairs malformed input. */
public final class BoundedJson {
    public static final int MAX_BYTES=1048576;
    private BoundedJson(){}
    public static JsonObject readObject(java.nio.file.Path path)throws IOException {
        try(InputStream in=java.nio.file.Files.newInputStream(path);ByteArrayOutputStream out=new ByteArrayOutputStream()){
            byte[] b=new byte[4096];int n;while((n=in.read(b))!=-1){if(out.size()+n>MAX_BYTES)throw new IOException("Config too large");out.write(b,0,n);}return object(out.toByteArray());
        }
    }
    public static JsonObject object(byte[] bytes)throws IOException {
        if(bytes.length>MAX_BYTES)throw new IOException("Config too large");
        String text=StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT).onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        try(JsonReader r=new JsonReader(new StringReader(text))){r.setLenient(false);JsonElement e=read(r,0,new int[]{0});if(!e.isJsonObject() || r.peek()!=JsonToken.END_DOCUMENT)throw new IOException("Expected one config object");return e.getAsJsonObject();}
        catch(IllegalStateException | NumberFormatException e){throw new IOException("Invalid config JSON",e);}
    }
    private static JsonElement read(JsonReader r,int depth,int[] nodes)throws IOException {
        if(depth>32 || ++nodes[0]>65536)throw new IOException("Config complexity limit");
        switch(r.peek()){
            case BEGIN_OBJECT:{JsonObject o=new JsonObject();r.beginObject();int count=0;while(r.hasNext()){if(++count>16384)throw new IOException("Too many config members");String key=r.nextName();if(key.length()>256 || o.has(key))throw new IOException("Invalid/duplicate config key");o.add(key,read(r,depth+1,nodes));}r.endObject();return o;}
            case BEGIN_ARRAY:{JsonArray a=new JsonArray();r.beginArray();while(r.hasNext()){if(a.size()>=16384)throw new IOException("Config array too large");a.add(read(r,depth+1,nodes));}r.endArray();return a;}
            case STRING:{String s=r.nextString();if(s.length()>16384)throw new IOException("Config string too long");return new JsonPrimitive(s);}
            case NUMBER:{String n=r.nextString();if(n.length()>64)throw new IOException("Config number too long");return new JsonPrimitive(new java.math.BigDecimal(n));}
            case BOOLEAN:return new JsonPrimitive(r.nextBoolean());
            case NULL:r.nextNull();return JsonNull.INSTANCE;
            default:throw new IOException("Invalid config token");
        }
    }
}
