package ru.nsu.ccfit.zuev.osu;

import static kotlin.collections.ArraysKt.any;
import static kotlin.collections.ArraysKt.filter;
import static kotlin.collections.ArraysKt.joinToString;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.util.Log;

import com.osudroid.ui.skinning.IniReader;
import com.osudroid.ui.skinning.SkinConverter;
import com.reco1l.andengine.UIEngine;
import com.reco1l.andengine.texture.BlankTextureRegion;
import org.anddev.andengine.engine.Engine;
import org.anddev.andengine.opengl.font.Font;
import org.anddev.andengine.opengl.font.FontFactory;
import org.anddev.andengine.opengl.font.StrokeFont;
import org.anddev.andengine.opengl.texture.TextureOptions;
import org.anddev.andengine.opengl.texture.atlas.bitmap.BitmapTextureAtlas;
import org.anddev.andengine.opengl.texture.region.TextureRegion;
import org.anddev.andengine.opengl.texture.region.TextureRegionFactory;
import org.anddev.andengine.util.Debug;
import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import kotlin.text.MatchResult;
import kotlin.text.Regex;
import ru.nsu.ccfit.zuev.audio.BassSoundProvider;
import ru.nsu.ccfit.zuev.osu.helper.FileUtils;
import ru.nsu.ccfit.zuev.osu.helper.MD5Calculator;
import ru.nsu.ccfit.zuev.osu.helper.QualityAssetBitmapSource;
import ru.nsu.ccfit.zuev.osu.helper.QualityFileBitmapSource;
import ru.nsu.ccfit.zuev.osu.online.OnlineManager;
import ru.nsu.ccfit.zuev.osuplus.BuildConfig;
import ru.nsu.ccfit.zuev.skins.OsuSkin;
import ru.nsu.ccfit.zuev.skins.SkinJsonReader;
import ru.nsu.ccfit.zuev.skins.BeatmapSkinManager;
import ru.nsu.ccfit.zuev.skins.StringSkinData;

public class ResourceManager {

    /**
     * The textures that shouldn't fallback to the default skin if they're not present in the skin folder.
     */
    private static final String[] OPTIONAL_TEXTURES = {
        "scorebar-marker",
        "scorebar-ki",
        "scorebar-kidanger",
        "scorebar-kidanger2",
    };

    /**
     * The textures that can be animated.
     */
    private static final String[] ANIMATABLE_TEXTURES = {
        "followpoint-",
        "hit0-",
        "hit100-",
        "hit100k-",
        "hit300-",
        "hit300g-",
        "hit300k-",
        "hit50-",
        "menu-back-",
        "play-skip-",
        "scorebar-colour-",
        "sliderb",
        "sliderfollowcircle-"
    };

    private static final Regex ANIMATABLE_TEXTURE_REGEX = new Regex("^(" + joinToString(ANIMATABLE_TEXTURES, "|", "", "", -1, "", null) + ")(\\d+)$");

    private final static ResourceManager mgr = new ResourceManager();

    private final Map<String, Font> fonts = new HashMap<>();
    private final Map<String, Integer> frameCount = new HashMap<>();
    private final Map<String, TextureRegion> textures = new HashMap<>();
    private final Map<String, BassSoundProvider> sounds = new HashMap<>();

    private final Map<String, Integer> customFrameCount = new HashMap<>();
    private final Map<String, TextureRegion> customTextures = new HashMap<>();
    private final Map<String, BassSoundProvider> customSounds = new HashMap<>();

    // Map dedicated to holding dynamic online profile banner textures
    private final Map<String, TextureRegion> profileBanners = new HashMap<>();

    private Engine engine;
    private Context context;

    private ResourceManager() {
    }

    public static ResourceManager getInstance() {
        return mgr;
    }

    public Engine getEngine() {
        return engine;
    }

    public void Init(final Engine engine, final Context context) {
        this.engine = engine;
        this.context = context;

        fonts.clear();
        textures.clear();
        sounds.clear();
        frameCount.clear();

        customSounds.clear();
        customTextures.clear();
        customFrameCount.clear();
        profileBanners.clear();
    }

    public void loadSkin(String folder) {
        loadFont("smallFont", null, 21, Color.WHITE);
        loadFont("middleFont", null, 24, Color.WHITE);
        loadFont("bigFont", null, 36, Color.WHITE);
        loadFont("font", null, 28, Color.WHITE);
        loadStrokeFont("strokeFont", null, 36, Color.BLACK, Color.WHITE);
        loadFont("CaptionFont", null, 35, Color.WHITE);

        if (!folder.endsWith("/"))
            folder = folder + "/";

        loadCustomSkin(folder);

        loadTexture("ranking_enabled_score", "ranking_enabled_score.png", false);
        loadTexture("ranking_enabled_pp", "ranking_enabled_pp.png", false);
        loadTexture("ranking_disabled", "ranking_disabled.png", false);
        loadTexture("flashlight_cursor", "flashlight_cursor.png", false, TextureOptions.BILINEAR_PREMULTIPLYALPHA);

        if (!textures.containsKey("lighting"))
            textures.put("lighting", null);

        UIEngine.getCurrent().onSkinChange();
    }

    public void loadCustomSkin(String folder) {
        if (!folder.endsWith("/")) folder += "/";

        File[] skinFiles = null;
        File skinFolder = new File(folder);
        if (!skinFolder.exists()) {
            skinFolder = null;
        } else {
            skinFiles = FileUtils.listFiles(skinFolder);
        }
        if (skinFiles != null) {
            JSONObject skinjson = null;
            File jsonFile = new File(folder, "skin.json");
            if (jsonFile.exists()) {
                try {
                    skinjson = new JSONObject(OsuSkin.readFull(jsonFile));
                } catch (Exception e) {
                    e.printStackTrace();
                }
            } else {
                var iniFile = new File(folder, "skin.ini");

                if (iniFile.exists()) {
                    GlobalManager.getInstance().setInfo("Reading skin.ini...");

                    try (var ini = new IniReader(iniFile)) {
                        skinjson = SkinConverter.convertToJson(ini);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }

                    SkinConverter.ensureOptionalTexture(new File(folder, "sliderendcircle.png"));
                    SkinConverter.ensureOptionalTexture(new File(folder, "sliderendcircleoverlay.png"));

                    SkinConverter.ensureTexture(new File(folder, "selection-mods.png"));
                    SkinConverter.ensureTexture(new File(folder, "selection-random.png"));
                    SkinConverter.ensureTexture(new File(folder, "selection-options.png"));

                    skinFiles = FileUtils.listFiles(skinFolder);
                }
            }
            if (skinjson == null) skinjson = new JSONObject();
            SkinJsonReader.getReader().supplyJson(skinjson);
        }
        final Map<String, File> availableFiles = new HashMap<>();
        if (skinFiles != null) {
            for (final File f : skinFiles) {
                if (f.isFile()) {
                    if (f.getName().startsWith("comboburst")
                            && (f.getName().endsWith(".wav") || f.getName().endsWith(".mp3"))) {
                        continue;
                    }
                    if (f.getName().length() < 5) {
                        continue;
                    }
                    if (f.length() == 0) {
                        continue;
                    }
                    final String filename = f.getName().substring(0, f.getName().length() - 4);
                    availableFiles.put(filename, f);

                    if (filename.equals("hitcircle")) {
                        if (!availableFiles.containsKey("sliderstartcircle")) {
                            availableFiles.put("sliderstartcircle", f);
                        }
                        if (!availableFiles.containsKey("sliderendcircle")) {
                            availableFiles.put("sliderendcircle", f);
                        }
                    }
                    if (filename.equals("hitcircleoverlay")) {
                        if (!availableFiles.containsKey("sliderstartcircleoverlay")) {
                            availableFiles.put("sliderstartcircleoverlay", f);
                        }
                        if (!availableFiles.containsKey("sliderendcircleoverlay")) {
                            availableFiles.put("sliderendcircleoverlay", f);
                        }
                    }
                }
            }
        }

        for (var key : textures.keySet().toArray(new String[0])) {
            if (any(ANIMATABLE_TEXTURES, key::startsWith)) {
                unloadTexture(key);
            }
        }

        frameCount.clear();
        customFrameCount.clear();

        try {
            String[] availableAnimatableFilenames = filter(availableFiles.keySet().toArray(new String[0]), f -> any(ANIMATABLE_TEXTURES, f::startsWith)).toArray(new String[0]);
            boolean isDefaultSkin = Objects.equals(folder, Config.getSkinTopPath());

            for (var assetName : Objects.requireNonNull(context.getAssets().list("gfx"))) {
                var textureName = assetName.substring(0, assetName.length() - 4);

                var skip = false;
                for (var animatableTexture : ANIMATABLE_TEXTURES) {
                    if (textureName.startsWith(animatableTexture) && any(availableAnimatableFilenames, f -> f.startsWith(animatableTexture))) {
                        skip = true;
                        break;
                    }
                }
                if (skip) {
                    continue;
                }

                if (availableFiles.containsKey(textureName)) {
                    loadTexture(textureName, Objects.requireNonNull(availableFiles.get(textureName)).getPath(), true);
                } else {
                    if (!isDefaultSkin && any(OPTIONAL_TEXTURES, textureName::startsWith)) {
                        unloadTexture(textureName);
                    } else {
                        loadTexture(textureName, "gfx/" + assetName, false);
                        parseFrameIndex(textureName, false, false);
                    }
                }
            }

            if (availableFiles.containsKey("scorebar-kidanger")) {
                loadTexture("scorebar-kidanger", Objects.requireNonNull(availableFiles.get("scorebar-kidanger")).getPath(), true);
                loadTexture("scorebar-kidanger2", Objects.requireNonNull(availableFiles.get(availableFiles.containsKey("scorebar-kidanger2") ? "scorebar-kidanger2" : "scorebar-kidanger")).getPath(), true);
            }

            if (availableFiles.containsKey("comboburst")) {
                loadTexture("comboburst", Objects.requireNonNull(availableFiles.get("comboburst")).getPath(), true);
            } else {
                unloadTexture("comboburst");
            }

            for (int i = 0; i < 10; i++) {
                String textureName = "comboburst-" + i;
                if (availableFiles.containsKey(textureName)) {
                    File file = availableFiles.get(textureName);
                    if (file != null) {
                        loadTexture(textureName, file.getPath(), true);
                    } else {
                        unloadTexture(textureName);
                    }
                }
            }

            for (var filename : availableAnimatableFilenames) {
                var file = availableFiles.get(filename);
                if (file != null) {
                    loadTexture(filename, file.getPath(), true);
                    parseFrameIndex(filename, false, false);
                } else {
                    unloadTexture(filename);
                }
            }

        } catch (final IOException e) {
            Debug.e("Resources: " + e.getMessage(), e);
        }

        try {
            for (final String s : Objects.requireNonNull(context.getAssets().list("sfx"))) {
                final String name = s.substring(0, s.length() - 4);
                if (availableFiles.containsKey(name)) {
                    loadSound(name, Objects.requireNonNull(availableFiles.get(name)).getPath(), true);
                } else {
                    loadSound(name, "sfx/" + s, false);
                }
            }
            if (skinFolder != null) {
                loadSound("comboburst", folder + "comboburst.wav", true);
                for (int i = 0; i < 10; i++) {
                    loadSound("comboburst-" + i, folder + "comboburst-" + i + ".wav", true);
                }
            }
        } catch (final IOException e) {
            Debug.e("Resources: " + e.getMessage(), e);
        }

        loadTexture("ranking_button", "ranking_button.png", false);
        loadTexture("ranking_enabled_score", "ranking_enabled_score.png", false);
        loadTexture("ranking_enabled_pp", "ranking_enabled_pp.png", false);
        loadTexture("ranking_disabled", "ranking_disabled.png", false);
        loadTexture("selection-approved", "selection-approved.png", false);
        loadTexture("selection-loved", "selection-loved.png", false);
        loadTexture("selection-question", "selection-question.png", false);
        loadTexture("selection-ranked", "selection-ranked.png", false);
        if (!textures.containsKey("lighting"))
            textures.put("lighting", null);
    }

    private int parseFrameIndex(String filename, boolean checkFirstFrameExists, boolean isBeatmapSkin) {
        String textureName = filename;
        int frameIndex = 0;

        MatchResult result = ANIMATABLE_TEXTURE_REGEX.matchEntire(filename);

        if (result != null) {
            List<String> values = result.getGroupValues();

            textureName = values.get(1);
            if (textureName.endsWith("-")) {
                textureName = textureName.substring(0, textureName.length() - 1);
            }

            frameIndex = Integer.parseInt(values.get(2));
        }

        var skinTextures = isBeatmapSkin ? customTextures : textures;
        var skinFrameCount = isBeatmapSkin ? customFrameCount : frameCount;

        if (result == null || checkFirstFrameExists
                && !skinTextures.containsKey(textureName)
                && !skinTextures.containsKey(textureName + "-0")
                && !skinTextures.containsKey(textureName + "0")) {
            skinFrameCount.remove(textureName);
            return -1;
        }

        if (!skinFrameCount.containsKey(textureName) || skinFrameCount.get(textureName) < frameIndex + 1) {
            skinFrameCount.put(textureName, frameIndex + 1);
        }

        if (BuildConfig.DEBUG) {
            Log.v("ResourceManager", "Parsed frame index: " + frameIndex + " from " + filename);
        }

        return frameIndex;
    }

    public Font loadFont(final String resname, final String file, int size, final int color) {
        size /= Config.getTextureQuality();
        final BitmapTextureAtlas texture = new BitmapTextureAtlas(512, 512, TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        Font font;
        if (file == null) {
            font = new Font(texture, Typeface.create(Typeface.DEFAULT, Typeface.NORMAL), size, true, color);
        } else {
            font = FontFactory.createFromAsset(texture, context, "fonts/" + file, size, true, color);
        }
        engine.getTextureManager().loadTexture(texture);
        engine.getFontManager().loadFont(font);
        fonts.put(resname, font);
        return font;
    }

    public StrokeFont loadStrokeFont(final String resname, final String file, int size, final int color1, final int color2) {
        size /= Config.getTextureQuality();
        final BitmapTextureAtlas texture = new BitmapTextureAtlas(512, 256, TextureOptions.BILINEAR_PREMULTIPLYALPHA);
        StrokeFont font;
        if (file == null) {
            font = new StrokeFont(texture, Typeface.create(Typeface.DEFAULT, Typeface.NORMAL), size, true, color1, Config.getTextureQuality() == 1 ? 2 : 0.75f, color2);
        } else {
            font = FontFactory.createStrokeFromAsset(texture, context, "fonts/" + file, size, true, color1, (float) 2 / Config.getTextureQuality(), color2);
        }
        engine.getTextureManager().loadTexture(texture);
        engine.getFontManager().loadFont(font);
        fonts.put(resname, font);
        return font;
    }

    public Font getFont(final String resname) {
        if (!fonts.containsKey(resname)) {
            loadFont(resname, null, 35, Color.WHITE);
        }
        return fonts.get(resname);
    }

    public TextureRegion loadTexture(final String resname, final String file, final boolean external, final TextureOptions opt) {
        return loadTexture(resname, file, external, opt, this.engine);
    }

    public TextureRegion loadTexture(final String resname, final String file, final boolean external) {
        return loadTexture(resname, file, external, TextureOptions.BILINEAR, this.engine);
    }

    public TextureRegion loadTexture(final String resname, final String file, final boolean external, Engine engine) {
        return loadTexture(resname, file, external, TextureOptions.BILINEAR, engine);
    }

    public TextureRegion loadTexture(final String resname, final String file, final boolean external, final TextureOptions opt, Engine engine) {
        TextureRegion region;
        try {
            if (external) {
                region = TextureRegionFactory.createFromSource(engine.getTextureManager(), new QualityFileBitmapSource(new File(file)), opt);
            } else {
                region = TextureRegionFactory.createFromSource(engine.getTextureManager(), new QualityAssetBitmapSource(context, file), opt);
            }
        } catch (Exception e) {
            Debug.e("Failed to load texture: " + resname + " from " + file, e);
            region = BlankTextureRegion.INSTANCE;
        }
        textures.put(resname, region);
        return region;
    }

    public void unloadTexture(final String resname) {
        if (textures.containsKey(resname)) {
            TextureRegion region = textures.remove(resname);
            if (region != null && engine != null) {
                engine.getTextureManager().unloadTexture(region.getTexture());
            }
        }
    }

    public TextureRegion getTexture(final String resname) {
        if (textures.containsKey(resname)) {
            return textures.get(resname);
        }
        return BlankTextureRegion.INSTANCE;
    }

    public TextureRegion loadBackground(final String file) {
        return loadBackground(file, this.engine);
    }

    public TextureRegion loadBackground(final String file, Engine engine) {
        if (textures.containsKey("::background")) {
            engine.getTextureManager().unloadTexture(Objects.requireNonNull(textures.get("::background")).getTexture());
        }
        if (file == null) {
            return textures.get("menu-background");
        }
        TextureRegion region;
        final QualityFileBitmapSource source = new QualityFileBitmapSource(new File(file));
        if (source.getWidth() == 0 || source.getHeight() == 0) {
            return textures.get("menu-background");
        }
        region = TextureRegionFactory.createFromSource(engine.getTextureManager(), source, TextureOptions.BILINEAR);
        textures.put("::background", region);
        return region;
    }

    public BassSoundProvider loadSound(final String resname, final String file, final boolean external) {
        BassSoundProvider sound = new BassSoundProvider();
        if (sound.prepare(file, external)) {
            sounds.put(resname, sound);
            return sound;
        }
        return null;
    }

    public BassSoundProvider getSound(final String resname) {
        return sounds.get(resname);
    }

    // --- Dynamic Profile Banner Management ---

    /**
     * Called by OnlineManager to check if a profile banner has already been loaded into memory.
     */
    public TextureRegion getProfileBannerTextureIfLoaded(String bannerURL) {
        if (bannerURL == null || bannerURL.isEmpty()) {
            return null;
        }
        return profileBanners.get(bannerURL);
    }

    /**
     * Stores a loaded profile banner texture region under its URL key.
     */
    public void loadProfileBannerTexture(String bannerURL, TextureRegion textureRegion) {
        if (bannerURL != null && textureRegion != null) {
            profileBanners.put(bannerURL, textureRegion);
        }
    }

    /**
     * Removes and unloads a specific profile banner texture from the engine memory.
     */
    public void unloadProfileBannerTexture(String bannerURL) {
        TextureRegion region = profileBanners.remove(bannerURL);
        if (region != null && engine != null) {
            engine.getTextureManager().unloadTexture(region.getTexture());
        }
    }
}