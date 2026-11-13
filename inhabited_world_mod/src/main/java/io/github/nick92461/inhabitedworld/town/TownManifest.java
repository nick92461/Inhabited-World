package io.github.nick92461.inhabitedworld.town;

import java.io.IOException;
import java.io.Reader;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import io.github.nick92461.inhabitedworld.InhabitedWorld;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/*
  A handcrafted town exported by tools/buildimport (python -m buildimport export): its structure-template tiles,
  semantic markers (canon 02 section 16) and the numbers placement needs. Read from the mod's data:

    data/inhabited_world/towns/<path>/v<version>/manifest.json
    data/inhabited_world/towns/<path>/v<version>/columns.nbt       per-column zones (TownColumns)
    data/inhabited_world/structure/<path>/v<version>/tile_<i>_<j>.nbt

  Template space: x east, z south, origin = the box's north-west corner at its lowest stored y. Every tile is placed
  at origin + tile offset. Only the fields placement and the workshop use are read here; the JSON has more
  (blend edge, joins, waterfront) for Phase 3.
 */
public record TownManifest(Identifier manifestId, String id, int version, String name, Vec3i size, int seaLevel,
                           int targetSeaLevel, List<Tile> tiles, List<Marker> markers, BlockPos workshopOrigin) {

    public record Tile(Identifier id, BlockPos offset, Vec3i size, int blocks, int nonAir) {
    }

    public record Marker(String id, BlockPos pos, float yaw, String note) {
    }

    private static final String ROOT = "towns";
    private static final String FILE = "manifest.json";

    // Identifier of the columns file that belongs to this manifest.
    public Identifier columnsId() {
        String p = manifestId.getPath();
        return Identifier.fromNamespaceAndPath(manifestId.getNamespace(), p.substring(0, p.length() - FILE.length()) + "columns.nbt");
    }

    // The world y at which template y = 0 goes so the town's sea level lands on the target sea level.
    public int baseY() {
        return targetSeaLevel - seaLevel;
    }

    // Short name used by commands and the workshop: "drachenfels/d1_harbour/v1".
    public String key() {
        return key(manifestId);
    }

    private static String key(Identifier manifest) {
        String p = manifest.getPath();
        return p.substring(ROOT.length() + 1, p.length() - FILE.length() - 1);
    }

    // Every town manifest in the loaded data, by key.
    public static Map<String, Identifier> list(ResourceManager resources) {
        Map<String, Identifier> out = new TreeMap<>();
        resources.listResources(ROOT, id -> id.getNamespace().equals(InhabitedWorld.MOD_ID) && id.getPath().endsWith("/" + FILE))
                .keySet().forEach(id -> out.put(key(id), id));
        return out;
    }

    // Finds a town by key ("drachenfels/d1_harbour/v1"), or by its last name ("d1_harbour", "drachenfels/d1_harbour"),
    // which picks the highest version.
    public static Optional<TownManifest> find(ResourceManager resources, String name) {
        Map<String, Identifier> all = list(resources);
        Identifier hit = all.get(name);
        if (hit == null) {
            hit = all.entrySet().stream()
                    .filter(e -> {
                        String withoutVersion = e.getKey().substring(0, e.getKey().lastIndexOf('/'));
                        return withoutVersion.equals(name) || withoutVersion.endsWith("/" + name);
                    })
                    .max(Comparator.comparingInt(e -> versionOf(e.getKey())))
                    .map(Map.Entry::getValue).orElse(null);
        }
        if (hit == null) {
            return Optional.empty();
        }
        return Optional.of(load(resources, hit));
    }

    private static int versionOf(String key) {
        String v = key.substring(key.lastIndexOf('/') + 1);
        try {
            return Integer.parseInt(v.startsWith("v") ? v.substring(1) : v);
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    public static TownManifest load(ResourceManager resources, Identifier manifestId) {
        Resource resource = resources.getResource(manifestId).orElseThrow(() -> new IllegalArgumentException("no town manifest " + manifestId));
        try (Reader reader = resource.openAsReader()) {
            return parse(manifestId, JsonParser.parseReader(reader).getAsJsonObject());
        } catch (IOException e) {
            throw new IllegalStateException("cannot read town manifest " + manifestId, e);
        }
    }

    static TownManifest parse(Identifier manifestId, JsonObject json) {
        JsonObject levels = json.getAsJsonObject("levels");
        List<Tile> tiles = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("tiles")) {
            JsonObject t = e.getAsJsonObject();
            tiles.add(new Tile(Identifier.parse(t.get("id").getAsString()), pos(t.getAsJsonArray("offset")),
                    pos(t.getAsJsonArray("size")), t.get("blocks").getAsInt(), t.get("non_air").getAsInt()));
        }
        List<Marker> markers = new ArrayList<>();
        for (JsonElement e : json.getAsJsonArray("markers")) {
            JsonObject m = e.getAsJsonObject();
            markers.add(new Marker(m.get("id").getAsString(), pos(m.getAsJsonArray("pos")), m.get("yaw").getAsFloat(),
                    m.has("note") ? m.get("note").getAsString() : ""));
        }
        JsonObject workshop = json.getAsJsonObject("workshop");
        return new TownManifest(manifestId, json.get("id").getAsString(), json.get("version").getAsInt(),
                json.get("name").getAsString(), pos(json.getAsJsonArray("size")), levels.get("sea_level").getAsInt(),
                levels.get("target_sea_level").getAsInt(), List.copyOf(tiles), List.copyOf(markers),
                pos(workshop.getAsJsonArray("origin")));
    }

    private static BlockPos pos(JsonArray a) {
        return new BlockPos(a.get(0).getAsInt(), a.get(1).getAsInt(), a.get(2).getAsInt());
    }
}
