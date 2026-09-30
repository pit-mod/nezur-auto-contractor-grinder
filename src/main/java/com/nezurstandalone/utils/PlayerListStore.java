package com.nezurstandalone.utils;

import net.minecraft.entity.player.EntityPlayer;

import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Unified storage for friend and truce lists (UUID -> display name).
 */
public final class PlayerListStore {

    public enum ListType {
        FRIEND,
        TRUCE
    }

    private static final Map<ListType, Map<String, String>> LISTS = new ConcurrentHashMap<>();
    private static final Map<ListType,Map<String,Integer>> NAME_COUNTS = new java.util.EnumMap<>(ListType.class);
    private static final Map<ListType, Set<String>> NAME_INDEX = new ConcurrentHashMap<>();

    static {
        for (ListType type : ListType.values()) {
            LISTS.put(type, new ConcurrentHashMap<>());
            NAME_INDEX.put(type, ConcurrentHashMap.newKeySet());
            NAME_COUNTS.put(type,new java.util.HashMap<>());
        }
    }

    private PlayerListStore() {
    }

    private static String clean(String name) {return ProfileLookup.getCleanName(name).toLowerCase(java.util.Locale.ROOT);}
    private static String identity(String value) {
        if(value==null)return null;
        String compact=value.trim().replace("-", "").toLowerCase(java.util.Locale.ROOT);
        if(compact.matches("[0-9a-f]{32}"))return compact.substring(0,8)+"-"+compact.substring(8,12)+"-"+compact.substring(12,16)+"-"+compact.substring(16,20)+"-"+compact.substring(20);
        String name=value.startsWith("name:")?value.substring(5):value;name=clean(name);return name.isEmpty()?null:"name:"+name;
    }
    private static void addIndex(ListType type,String name){String n=clean(name);Map<String,Integer> counts=NAME_COUNTS.get(type);counts.put(n,counts.getOrDefault(n,0)+1);NAME_INDEX.get(type).add(n);}
    private static void removeIndex(ListType type,String name){if(name==null)return;String n=clean(name);Map<String,Integer> counts=NAME_COUNTS.get(type);int left=counts.getOrDefault(n,0)-1;if(left<=0){counts.remove(n);NAME_INDEX.get(type).remove(n);}else counts.put(n,left);}
    private static void upsert(ListType type,String uuid,String name){
        String key=identity(uuid);if(key==null || name==null || clean(name).isEmpty())return;Map<String,String> map=LISTS.get(type);
        if(key.startsWith("name:")){
            for(Map.Entry<String,String> e:map.entrySet())if(!e.getKey().startsWith("name:") && clean(e.getValue()).equals(clean(name)))return;
        }else removeIndex(type,map.remove("name:"+clean(name)));
        removeIndex(type,map.put(key,name));addIndex(type,name);
    }
    public static synchronized void add(ListType type, String name, String uuid) {upsert(type,uuid,name);}

    public static synchronized void removeByName(ListType type, String name) {
        if (name == null) {
            return;
        }
        String target=clean(name);Map<String,String> map=LISTS.get(type);
        for(java.util.Iterator<Map.Entry<String,String>> it=map.entrySet().iterator();it.hasNext();){Map.Entry<String,String> e=it.next();if(clean(e.getValue()).equals(target)){removeIndex(type,e.getValue());it.remove();}}
    }

    public static synchronized Map<String, String> get(ListType type) {
        return java.util.Collections.unmodifiableMap(new java.util.LinkedHashMap<>(LISTS.get(type)));
    }

    public static synchronized void clear(ListType type) {
        LISTS.get(type).clear();
        NAME_INDEX.get(type).clear();
        NAME_COUNTS.get(type).clear();
    }

    public static synchronized void loadEntry(ListType type, String uuidKey, String displayName) {upsert(type,uuidKey,displayName);}

    public static synchronized boolean containsPlayer(ListType type, EntityPlayer player) {
        if (player == null) {
            return false;
        }
        String uuid = identity(player.getUniqueID().toString());
        Map<String, String> map = LISTS.get(type);
        if (map.containsKey(uuid) || map.containsKey(uuid.replace("-", ""))) {
            return true;
        }
        if (containsByName(type, player.getName())) {
            return true;
        }
        String denicked = DenickManager.get(player.getName());
        if (denicked != null) {
            if (map.containsValue(denicked) || containsByName(type, denicked)) {
                return true;
            }
        }
        return false;
    }

    public static synchronized boolean containsByName(ListType type, String name) {
        if (name == null || name.isEmpty()) {
            return false;
        }
        String clean = clean(name);
        if (NAME_INDEX.get(type).contains(clean)) {
            return true;
        }
        String denicked = DenickManager.get(name);
        if (denicked != null && !denicked.equalsIgnoreCase(name)) {
            return NAME_INDEX.get(type).contains(clean(denicked));
        }
        return false;
    }

    public static synchronized boolean containsByUuid(ListType type, String uuid) {
        if (uuid == null) {
            return false;
        }
        return getStoredName(type, uuid) != null;
    }

    public static synchronized String getStoredName(ListType type, String uuid) {
        if (uuid == null) {
            return null;
        }
        String key=identity(uuid);return key==null?null:LISTS.get(type).get(key);
    }

    /**
     * @return list type if the player is on that list (UUID first, then name fallback)
     */
    public static synchronized ListType getListType(String uuid, String name) {
        if (uuid != null) {
            for (ListType type : ListType.values()) {
                if (containsByUuid(type, uuid)) {
                    return type;
                }
            }
        }
        if (name != null && !name.isEmpty()) {
            for (ListType type : ListType.values()) {
                if (containsByName(type, name)) {
                    return type;
                }
            }
        }
        return null;
    }

    /**
     * Updates the stored display name when tab shows a new username for a known UUID.
     *
     * @return previous stored name if it changed, otherwise null
     */
    public static synchronized String syncDisplayName(ListType type, String uuid, String tabName) {
        if (uuid == null || tabName == null || tabName.isEmpty()) {
            return null;
        }
        String key=identity(uuid);if(key==null)return null;
        String stored=LISTS.get(type).get(key);
        if(stored==null || stored.equalsIgnoreCase(tabName))return null;
        upsert(type,key,tabName);
        return stored;
    }
}
