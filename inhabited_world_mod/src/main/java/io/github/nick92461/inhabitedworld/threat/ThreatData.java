package io.github.nick92461.inhabitedworld.threat;

import java.io.Reader;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Function;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;

import io.github.nick92461.inhabitedworld.InhabitedWorld;
import io.github.nick92461.inhabitedworld.encounter.EncounterDefinition;

import net.minecraft.resources.FileToIdConverter;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

/*
  The threat content of the loaded data packs (canon 10 section 57: authored content apart from engine rules):

    data/<ns>/threat_profile/<path>.json   ThreatProfile.Definition, ID <ns>:<path>
    data/<ns>/poi_threats/<path>.json      PoiThreatBinding, for the POI catalog ID <ns>:<path>
    data/<ns>/encounter/<path>.json        EncounterDefinition, ID <ns>:<path>

  Read when a world's regions are installed and again after /reload. A file that doesn't decode is logged and left
  out; the world then runs without it (a POI without its profile behaves like the wilderness, and says so).
 */
public record ThreatData(Map<Identifier, ThreatProfile> profiles, Map<Identifier, PoiThreatBinding> poiBindings,
                         Map<Identifier, EncounterDefinition> encounters) {
    public static final FileToIdConverter PROFILES = FileToIdConverter.json("threat_profile");
    public static final FileToIdConverter POI_BINDINGS = FileToIdConverter.json("poi_threats");
    public static final FileToIdConverter ENCOUNTERS = FileToIdConverter.json("encounter");

    public static ThreatData load(ResourceManager resources) {
        Map<Identifier, ThreatProfile.Definition> definitions = read(resources, PROFILES, ThreatProfile.Definition.codec());
        Map<Identifier, ThreatProfile> profiles = new LinkedHashMap<>();
        definitions.forEach((id, definition) -> profiles.put(id, new ThreatProfile(id, definition)));
        return new ThreatData(Map.copyOf(profiles), read(resources, POI_BINDINGS, PoiThreatBinding.CODEC), read(resources, ENCOUNTERS, EncounterDefinition.CODEC));
    }

    private static <T> Map<Identifier, T> read(ResourceManager resources, FileToIdConverter files, Codec<T> codec) {
        Map<Identifier, T> out = new LinkedHashMap<>();
        for (Map.Entry<Identifier, Resource> file : files.listMatchingResources(resources).entrySet()) {
            Identifier id = files.fileToId(file.getKey());
            try (Reader reader = file.getValue().openAsReader()) {
                T value = parse(codec, JsonParser.parseReader(reader), id.toString());
                if (value != null) {
                    out.put(id, value);
                }
            } catch (Exception e) {
                InhabitedWorld.LOGGER.error("[{}] could not read {}: {}", InhabitedWorld.MOD_ID, file.getKey(), e.toString());
            }
        }
        return Map.copyOf(out);
    }

    /** Decodes one file; null (and an error in the log) when it doesn't fit its format. */
    public static <T> T parse(Codec<T> codec, JsonElement json, String what) {
        DataResult<T> result = codec.parse(JsonOps.INSTANCE, json);
        if (result.isError()) {
            // No partial results: a half-read profile would silently change a POI's rules.
            InhabitedWorld.LOGGER.error("[{}] {} is not valid: {}", InhabitedWorld.MOD_ID, what, result.error().map(DataResult.Error::message).orElse("?"));
            return null;
        }
        return result.getOrThrow();
    }

    public static <K, V> String count(Map<K, V> map, Function<K, String> name) {
        return map.size() + (map.isEmpty() ? "" : " (" + String.join(", ", map.keySet().stream().map(name).sorted().toList()) + ")");
    }
}
