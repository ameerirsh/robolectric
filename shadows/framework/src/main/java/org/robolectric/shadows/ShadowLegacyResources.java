package org.robolectric.shadows;

import static android.os.Build.VERSION_CODES.KITKAT_WATCH;
import static android.os.Build.VERSION_CODES.LOLLIPOP;
import static android.os.Build.VERSION_CODES.M;
import static android.os.Build.VERSION_CODES.N;
import static android.os.Build.VERSION_CODES.N_MR1;
import static org.robolectric.shadow.api.Shadow.directlyOn;
import static org.robolectric.shadows.ShadowAssetManager.legacyShadowOf;
import static org.robolectric.shadows.ShadowAssetManager.useLegacy;

import android.content.res.AssetFileDescriptor;
import android.content.res.AssetManager;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.content.res.Resources.Theme;
import android.content.res.ResourcesImpl;
import android.content.res.TypedArray;
import android.content.res.XmlResourceParser;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.os.ParcelFileDescriptor;
import android.util.AttributeSet;
import android.util.DisplayMetrics;
import android.util.LongSparseArray;
import android.util.TypedValue;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.HiddenApi;
import org.robolectric.annotation.Implementation;
import org.robolectric.annotation.Implements;
import org.robolectric.annotation.RealObject;
import org.robolectric.annotation.Resetter;
import org.robolectric.res.Plural;
import org.robolectric.res.PluralRules;
import org.robolectric.res.ResName;
import org.robolectric.res.ResType;
import org.robolectric.res.ResourceTable;
import org.robolectric.res.TypedResource;
import org.robolectric.shadow.api.Shadow;
import org.robolectric.shadows.ShadowResources.Picker;
import org.robolectric.util.ReflectionHelpers;
import org.robolectric.util.ReflectionHelpers.ClassParameter;

@Implements(value = Resources.class, shadowPicker = Picker.class)
public class ShadowLegacyResources extends ShadowResources {
  private static Resources system = null;

  @RealObject Resources realResources;

  @Resetter
  public static void reset() {
    if (useLegacy()) {
      ShadowResources.reset();
    }
  }

  @Implementation
  protected static Resources getSystem() {
    if (system == null) {
      AssetManager assetManager = AssetManager.getSystem();
      DisplayMetrics metrics = new DisplayMetrics();
      Configuration config = new Configuration();
      system = new Resources(assetManager, metrics, config);
    }
    return system;
  }

  @Implementation
  protected TypedArray obtainAttributes(AttributeSet set, int[] attrs) {
    return legacyShadowOf(realResources.getAssets())
        .attrsToTypedArray(realResources, set, attrs, 0, 0, 0);
  }

  @Implementation
  protected String getQuantityString(int id, int quantity, Object... formatArgs)
      throws Resources.NotFoundException {
    String raw = getQuantityString(id, quantity);
    return String.format(Locale.ENGLISH, raw, formatArgs);
  }

  @Implementation
  protected String getQuantityString(int resId, int quantity) throws Resources.NotFoundException {
    ShadowLegacyAssetManager shadowAssetManager = legacyShadowOf(realResources.getAssets());

    TypedResource typedResource = shadowAssetManager.getResourceTable()
        .getValue(resId, shadowAssetManager.config);
    if (typedResource != null && typedResource instanceof PluralRules) {
      PluralRules pluralRules = (PluralRules) typedResource;
      Plural plural = pluralRules.find(quantity);

      if (plural == null) {
        return null;
      }

      TypedResource<?> resolvedTypedResource = shadowAssetManager.resolve(
          new TypedResource<>(plural.getString(), ResType.CHAR_SEQUENCE, pluralRules.getXmlContext()),
          shadowAssetManager.config, resId);
      return resolvedTypedResource == null ? null : resolvedTypedResource.asString();
    } else {
      return null;
    }
  }

  @Implementation
  protected InputStream openRawResource(int id) throws Resources.NotFoundException {
    ShadowLegacyAssetManager shadowAssetManager = legacyShadowOf(realResources.getAssets());
    ResourceTable resourceTable = shadowAssetManager.getResourceTable();
    InputStream inputStream = resourceTable.getRawValue(id, shadowAssetManager.config);
    if (inputStream == null) {
      throw newNotFoundException(id);
    } else {
      return inputStream;
    }
  }

  /**
   * Since {@link AssetFileDescriptor}s are not yet supported by Robolectric, {@code null} will be
   * returned if the resource is found. If the resource cannot be found, {@link
   * Resources.NotFoundException} will be thrown.
   */
  @Implementation
  protected AssetFileDescriptor openRawResourceFd(int id) throws Resources.NotFoundException {
    InputStream inputStream = openRawResource(id);
    if (!(inputStream instanceof FileInputStream)) {
      // todo fixme
      return null;
    }

    FileInputStream fis = (FileInputStream) inputStream;
    try {
      return new AssetFileDescriptor(ParcelFileDescriptor.dup(fis.getFD()), 0,
          fis.getChannel().size());
    } catch (IOException e) {
      throw newNotFoundException(id);
    }
  }

  private Resources.NotFoundException newNotFoundException(int id) {
    ResourceTable resourceTable = legacyShadowOf(realResources.getAssets()).getResourceTable();
    ResName resName = resourceTable.getResName(id);
    if (resName == null) {
      return new Resources.NotFoundException("resource ID #0x" + Integer.toHexString(id));
    } else {
      return new Resources.NotFoundException(resName.getFullyQualifiedName());
    }
  }

  @Implementation
  protected TypedArray obtainTypedArray(int id) throws Resources.NotFoundException {
    ShadowLegacyAssetManager shadowAssetManager = legacyShadowOf(realResources.getAssets());
    TypedArray typedArray = shadowAssetManager.getTypedArrayResource(realResources, id);
    if (typedArray != null) {
      return typedArray;
    } else {
      throw newNotFoundException(id);
    }
  }

  @HiddenApi
  @Implementation
  protected XmlResourceParser loadXmlResourceParser(int resId, String type)
      throws Resources.NotFoundException {
    ShadowLegacyAssetManager shadowAssetManager = legacyShadowOf(realResources.getAssets());
    return shadowAssetManager.loadXmlResourceParser(resId, type);
  }

  @HiddenApi
  @Implementation
  protected XmlResourceParser loadXmlResourceParser(
      String file, int id, int assetCookie, String type) throws Resources.NotFoundException {
    return loadXmlResourceParser(id, type);
  }

  @Implements(value = Resources.Theme.class, shadowPicker = ShadowTheme.Picker.class)
  public static class ShadowLegacyTheme extends ShadowTheme {
    @RealObject Resources.Theme realTheme;

    long getNativePtr() {
      if (RuntimeEnvironment.getApiLevel() >= N) {
        ResourcesImpl.ThemeImpl themeImpl = ReflectionHelpers.getField(realTheme, "mThemeImpl");
        return ((ShadowResourcesImpl.ShadowThemeImpl) Shadow.extract(themeImpl)).getNativePtr();
      } else {
        return ((Number) ReflectionHelpers.getField(realTheme, "mTheme")).longValue();
      }
    }

    @Implementation(maxSdk = M)
    protected TypedArray obtainStyledAttributes(int[] attrs) {
      return obtainStyledAttributes(0, attrs);
    }

    @Implementation(maxSdk = M)
    protected TypedArray obtainStyledAttributes(int resid, int[] attrs)
        throws Resources.NotFoundException {
      return obtainStyledAttributes(null, attrs, 0, resid);
    }

    @Implementation(maxSdk = M)
    protected TypedArray obtainStyledAttributes(
        AttributeSet set, int[] attrs, int defStyleAttr, int defStyleRes) {
      return getShadowAssetManager().attrsToTypedArray(getResources(), set, attrs, defStyleAttr, getNativePtr(), defStyleRes);
    }

    private ShadowLegacyAssetManager getShadowAssetManager() {
      return legacyShadowOf(getResources().getAssets());
    }

    private Resources getResources() {
      return ReflectionHelpers.getField(realTheme, "this$0");
    }
  }

  @HiddenApi
  @Implementation(maxSdk = KITKAT_WATCH)
  protected Drawable loadDrawable(TypedValue value, int id) {
    Drawable drawable = directlyOn(realResources, Resources.class, "loadDrawable",
        ClassParameter.from(TypedValue.class, value), ClassParameter.from(int.class, id));
    setCreatedFromResId(realResources, id, drawable);
    return drawable;
  }

  @Implementation(minSdk = LOLLIPOP, maxSdk = N_MR1)
  protected Drawable loadDrawable(TypedValue value, int id, Resources.Theme theme)
      throws Resources.NotFoundException {
    Drawable drawable = directlyOn(realResources, Resources.class, "loadDrawable",
        ClassParameter.from(TypedValue.class, value), ClassParameter.from(int.class, id), ClassParameter.from(Resources.Theme.class, theme));
    setCreatedFromResId(realResources, id, drawable);
    return drawable;
  }

  static void setCreatedFromResId(Resources resources, int id, Drawable drawable) {
    // todo: this kinda sucks, find some better way...
    if (drawable != null && Shadow.extract(drawable) instanceof ShadowDrawable) {
      ShadowDrawable shadowDrawable = Shadow.extract(drawable);
      shadowDrawable.createdFromResId = id;
      if (drawable instanceof BitmapDrawable) {
        Bitmap bitmap = ((BitmapDrawable) drawable).getBitmap();
        if (bitmap != null  && Shadow.extract(bitmap) instanceof ShadowBitmap) {
          ShadowBitmap shadowBitmap = Shadow.extract(bitmap);
          if (shadowBitmap.createdFromResId == -1) {
            shadowBitmap.setCreatedFromResId(id, resources.getResourceName(id));
          }
        }
      }
    }
  }
}
