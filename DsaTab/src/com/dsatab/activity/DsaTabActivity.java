package com.dsatab.activity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.json.JSONObject;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.app.LoaderManager;
import android.app.SearchManager;
import android.content.Context;
import android.content.Intent;
import android.content.Loader;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.LightingColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.os.Vibrator;
import android.support.v4.view.MenuItemCompat;
import android.support.v7.app.ActionBarDrawerToggle;
import android.support.v7.graphics.Palette;
import android.support.v7.graphics.Palette.PaletteAsyncListener;
import android.text.TextUtils;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Toast;

import com.dsatab.DsaTabApplication;
import com.dsatab.R;
import com.dsatab.cloud.HeroSaveTask;
import com.dsatab.cloud.HeroesLoaderTask;
import com.dsatab.config.TabInfo;
import com.dsatab.data.AbstractBeing;
import com.dsatab.data.Hero;
import com.dsatab.data.HeroConfiguration;
import com.dsatab.data.HeroFileInfo;
import com.dsatab.data.HeroLoaderTask;
import com.dsatab.data.Probe;
import com.dsatab.fragment.BaseFragment;
import com.dsatab.fragment.DiceSliderFragment;
import com.dsatab.fragment.HeroChooserFragment;
import com.dsatab.util.Debug;
import com.dsatab.util.Util;
import com.dsatab.view.listener.ShakeListener;
import com.gandulf.guilib.util.ResUtil;
import com.heinrichreimersoftware.materialdrawer.DrawerFrameLayout;
import com.heinrichreimersoftware.materialdrawer.DrawerView;
import com.heinrichreimersoftware.materialdrawer.structure.DrawerHeaderItem;
import com.heinrichreimersoftware.materialdrawer.structure.DrawerProfile;
import com.heinrichreimersoftware.materialdrawer.structure.DrawerProfile.OnProfileSwitchListener;
import com.nostra13.universalimageloader.core.assist.FailReason;
import com.nostra13.universalimageloader.core.listener.ImageLoadingListener;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

public class DsaTabActivity extends BaseActivity implements OnSharedPreferenceChangeListener,
		com.heinrichreimersoftware.materialdrawer.structure.DrawerItem.OnItemClickListener, OnProfileSwitchListener,
		ImageLoadingListener, PaletteAsyncListener {

	private static final String TAB_INDEX = "TAB_INDEX";

	private static final int DRAWER_ID_HEROES = 1001;
	private static final int DRAWER_ID_ITEMS = 1002;
	private static final int DRAWER_ID_SETTINGS = 1003;
	private static final int DRAWER_ID_TABS = 1004;

	protected static final String INTENT_TAB_INFO = "tabInfo";

	public static final String PREF_LAST_HERO = "LAST_HERO_JSON";

	private static final String KEY_HERO_PATH = "HERO_PATH";

	public static final int ACTION_PREFERENCES = 1000;
	public static final int ACTION_ADD_MODIFICATOR = 1003;
	protected static final int ACTION_CHOOSE_HERO = 1004;
	public static final int ACTION_EDIT_MODIFICATOR = 1005;
	public static final int ACTION_EDIT_TABS = 1006;
	public static final int ACTION_EDIT_NOTES = 1007;
	public static final int ACTION_EDIT_CUSTOM_PROBES = 1008;
	public static final int ACTION_VIEW_SPELL = 1009;
	public static final int ACTION_VIEW_ART = 1010;
	public static final int ACTION_HERO_CHOOSER = 1011;

	private static final String KEY_TAB_INFO = "tabInfo";

	private static final int LOADER_HERO_INFOS = 1;
	private static final int LOADER_HERO = 2;

	private static final int[] containerIds = { R.id.pane_left, R.id.pane_right };
	private List<ViewGroup> containers;

	protected SharedPreferences preferences;

	private DiceSliderFragment diceSliderFragment;

	private ShakeListener mShaker;

	private TabInfo tabInfo;

	private DrawerFrameLayout mDrawerLayout;
	private DrawerView mDrawer;
	private ActionBarDrawerToggle drawerToggle;

	private long backPressed;

	private HeroFileInfo heroFileInfo;

	private static class HeroLoaderCallback implements LoaderManager.LoaderCallbacks<Hero> {

		private DsaTabActivity context;

		public HeroLoaderCallback(DsaTabActivity context) {
			this.context = context;
		}

		@Override
		public Loader<Hero> onCreateLoader(int id, Bundle args) {
			if (id == LOADER_HERO) {
				return new HeroLoaderTask(context, (HeroFileInfo) args.getSerializable(KEY_HERO_PATH));
			} else {
				return null;
			}
		}

		@Override
		public void onLoadFinished(Loader<Hero> loader, Hero hero) {
			Debug.verbose("loading of hero finished");
			context.setToolbarRefreshing(false);

			// Swap the new cursor in. (The framework will take care of closing the
			// old cursor once we return.)
			if (loader instanceof HeroLoaderTask) {
				HeroLoaderTask heroLoader = (HeroLoaderTask) loader;

				if (heroLoader.getException() != null) {
					Toast.makeText(context, heroLoader.getException().getLocalizedMessage(), Toast.LENGTH_SHORT).show();
				}
			}
			DsaTabApplication.getInstance().setHero(hero);

			if (hero != null) {
				context.checkHsVersion(hero);
			}
			context.onHeroLoaded(hero);
		}

		@Override
		public void onLoaderReset(Loader<Hero> loader) {
			// This is called when the last Cursor provided to onLoadFinished()
			// above is about to be closed. We need to make sure we are no
			// longer using it.
			// mAdapter.swapCursor(null);
		}
	}

	private static class HeroesLoaderCallback implements LoaderManager.LoaderCallbacks<List<HeroFileInfo>> {

		private DsaTabActivity context;

		public HeroesLoaderCallback(DsaTabActivity context, DrawerView drawerView) {
			this.context = context;
		}

		@Override
		public Loader<List<HeroFileInfo>> onCreateLoader(int id, Bundle args) {
			if (id == LOADER_HERO_INFOS) {
				return new HeroesLoaderTask(context);
			} else {
				return null;
			}
		}

		@Override
		public void onLoaderReset(Loader<List<HeroFileInfo>> loader) {

		}

		@Override
		public void onLoadFinished(Loader<List<HeroFileInfo>> loader, List<HeroFileInfo> heroes) {
			if (loader instanceof HeroesLoaderTask) {
				Debug.verbose("loading of hero files finished");
				HeroesLoaderTask heroLoader = (HeroesLoaderTask) loader;

				for (Exception e : heroLoader.getExceptions()) {
					Toast.makeText(context, e.getLocalizedMessage(), Toast.LENGTH_SHORT).show();
					Debug.error(e);
				}

			}
			context.setupDrawerProfiles(heroes);
		}

	}

	public Hero getHero() {
		return DsaTabApplication.getInstance().getHero();
	}

	public final void loadHero(HeroFileInfo heroPath) {

		if (getHero() != null && preferences.getBoolean(DsaTabPreferenceActivity.KEY_AUTO_SAVE, true)) {
			HeroSaveTask heroSaveTask = new HeroSaveTask(this, getHero());
			heroSaveTask.execute();
		}
		setToolbarRefreshing(true);

		heroFileInfo = heroPath;
		Bundle args = new Bundle();
		args.putSerializable(KEY_HERO_PATH, heroPath);
		getLoaderManager().restartLoader(LOADER_HERO, args, new HeroLoaderCallback(this));
	}

	@Override
	public boolean onSearchRequested() {
		Bundle appData = new Bundle();
		appData.putParcelable(SearchableActivity.INTENT_TAB_INFO, tabInfo);
		startSearch(null, false, appData, false);
		return true;
	}

	private void updatePage(TabInfo tabInfo, FragmentTransaction ft) {

		if (ft != null && diceSliderFragment != null && diceSliderFragment.isAdded()
				&& diceSliderFragment.getView() != null) {
			if (tabInfo != null && tabInfo.isDiceSlider()) {
				ft.show(diceSliderFragment);

				// if (diceSliderFragment.getView().getVisibility() == View.GONE) {
				// diceSliderFragment.getView().setVisibility(View.VISIBLE);
				//
				// Animation animation = AnimationUtils.loadAnimation(this, R.anim.slide_in_bottom);
				// diceSliderFragment.getView().startAnimation(animation);
				// }

			} else {
				ft.hide(diceSliderFragment);

				// if (diceSliderFragment.getView().getVisibility() == View.VISIBLE) {
				// Animation animation = AnimationUtils.loadAnimation(this, R.anim.slide_out_bottom);
				// diceSliderFragment.getView().startAnimation(animation);
				// diceSliderFragment.getView().setVisibility(View.GONE);
				// }
			}
		}

		if (tabInfo != null) {
			getSupportActionBar().setTitle(tabInfo.getTitle());

			int tabIndex = getHeroConfiguration().getTabs().indexOf(tabInfo);
			if (tabIndex >= 0) {
				mDrawer.selectItem(tabIndex);
			}

			if (tabInfo.getIconUri() != null) {
				Drawable d = ResUtil.getDrawableByUri(this, tabInfo.getIconUri());
				if (d != null) {
					d = d.mutate();
					d.setBounds(0, 0, 200, 200);
					if (tabInfo.getColor() != Color.TRANSPARENT) {
						d.setColorFilter(new LightingColorFilter(tabInfo.getColor(), 1));
					}
					getSupportActionBar().setIcon(d);
				} else {
					getSupportActionBar().setIcon(R.drawable.icon);
				}
			} else {
				getSupportActionBar().setIcon(R.drawable.icon);
			}
		} else {
			getSupportActionBar().setIcon(R.drawable.icon);
		}

	}

	private boolean initHero() {

		// in case of orientation change the hero is already loaded, just recreate the menu etc...
		if (DsaTabApplication.getInstance().getHero() != null) {
			onHeroLoaded(DsaTabApplication.getInstance().getHero());
			return true;
		} else {
			String heroFileInfoJson = preferences.getString(PREF_LAST_HERO, null);
			if (heroFileInfoJson != null) {
				try {
					HeroFileInfo fileInfo = new HeroFileInfo(new JSONObject(heroFileInfoJson));
					loadHero(fileInfo);
					return true;
				} catch (Exception e) {
					Debug.error(e);
					showHeroChooser();
					return false;
				}
			} else {
				showHeroChooser();
				return false;
			}
		}
	}

	protected boolean checkHsVersion(Hero hero) {

		if (TextUtils.isEmpty(hero.getFileInfo().getVersion())) {
			Toast.makeText(this, getString(R.string.hero_loaded, hero.getName()), Toast.LENGTH_SHORT).show();
			return false;
		}

		int version = hero.getFileInfo().getVersionInt();

		if (version < 0) {
			Toast.makeText(
					this,
					"Warnung: Unbekannte Helden-Software Version. Es kann keine vollständige Kompatibilität gewährleistet werden.",
					Toast.LENGTH_LONG).show();
			return false;
		} else if (version > DsaTabApplication.HS_VERSION_INT) {
			Toast.makeText(this, "Hinweis: DsaTab wurde noch nicht an die aktuellste Helden-Software angepasst.",
					Toast.LENGTH_LONG).show();
			return false;
			// } else if (version < DsaTabApplication.HS_VERSION_INT) {
			// Toast.makeText(this,
			// "Warnung: Die Helden Xml Datei wurde nicht mit der aktuellen Helden-Software erstellt.",
			// Toast.LENGTH_LONG).show();
			// return false;
		} else {
			Toast.makeText(this, getString(R.string.hero_loaded, hero.getName()), Toast.LENGTH_SHORT).show();
			return true;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onActivityResult(int, int, android.content.Intent)
	 */
	@Override
	protected void onActivityResult(int requestCode, int resultCode, Intent data) {

		if (requestCode == ACTION_CHOOSE_HERO) {

			if (resultCode == RESULT_OK) {
				HeroFileInfo heroFileInfo = (HeroFileInfo) data
						.getSerializableExtra(HeroChooserFragment.INTENT_NAME_HERO_FILE_INFO);
				Debug.verbose("HeroChooserActivity returned with path:" + heroFileInfo);
				loadHero(heroFileInfo);
			} else if (resultCode == RESULT_CANCELED) {
				if (getHero() == null) {
					finish();
				}
			}
		} else if (requestCode == ACTION_PREFERENCES) {

			SharedPreferences preferences = DsaTabApplication.getPreferences();

			if (preferences.getBoolean(DsaTabPreferenceActivity.KEY_PROBE_SHAKE_ROLL_DICE, false)) {
				registerShakeDice();
			} else {
				unregisterShakeDice();
			}
		} else if (requestCode == ACTION_EDIT_TABS) {
			if (resultCode == Activity.RESULT_OK) {
				setupDrawerItems(getHero());
				showTab(tabInfo, false);
			}
		}

		// notify other listeners (fragments, heroes)
		for (int i = 0; i < TabInfo.MAX_TABS_PER_PAGE; i++) {
			if (getCurrentFragment(i) != null) {
				getCurrentFragment(i).onActivityResult(requestCode, resultCode, data);
			}
		}

		super.onActivityResult(requestCode, resultCode, data);
	}

	protected Fragment getCurrentFragment(int index) {
		if (tabInfo != null) {
			return getFragmentManager().findFragmentByTag(tabInfo.getId().toString() + index);
		} else {
			return null;
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		setTheme(DsaTabApplication.getInstance().getCustomTheme());
		applyPreferencesToTheme();
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main_tab_view);

		preferences = DsaTabApplication.getPreferences();

		DsaTabApplication.getPreferences().registerOnSharedPreferenceChangeListener(this);

		// start tracing to "/sdcard/calc.trace"
		// android.os.Debug.startMethodTracing("dsatab");

		getSupportActionBar().setDisplayShowHomeEnabled(true);
		getSupportActionBar().setDisplayHomeAsUpEnabled(true);
		getSupportActionBar().setHomeButtonEnabled(true);

		containers = new ArrayList<ViewGroup>(2);
		for (int i = 0; i < containerIds.length; i++) {
			containers.add((ViewGroup) findViewById(containerIds[i]));
		}

		SlidingUpPanelLayout slidingUpPanelLayout = (SlidingUpPanelLayout) findViewById(R.id.slidepanel);

		String orientation = preferences.getString(DsaTabPreferenceActivity.KEY_SCREEN_ORIENTATION,
				DsaTabPreferenceActivity.DEFAULT_SCREEN_ORIENTATION);

		Configuration configuration = getResources().getConfiguration();

		if (savedInstanceState != null) {
			tabInfo = savedInstanceState.getParcelable(KEY_TAB_INFO);
		}

		if (tabInfo == null && getIntent() != null) {
			tabInfo = getIntent().getParcelableExtra(INTENT_TAB_INFO);
		}

		diceSliderFragment = (DiceSliderFragment) getFragmentManager().findFragmentByTag(DiceSliderFragment.TAG);
		diceSliderFragment.setSlidingUpPanelLayout(slidingUpPanelLayout);

		// Debug.verbose("onCreate Orientation =" + configuration.orientation);
		if (DsaTabPreferenceActivity.SCREEN_ORIENTATION_LANDSCAPE.equals(orientation)
				&& configuration.orientation != Configuration.ORIENTATION_LANDSCAPE) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
			return;
		} else if (DsaTabPreferenceActivity.SCREEN_ORIENTATION_PORTRAIT.equals(orientation)
				&& configuration.orientation != Configuration.ORIENTATION_PORTRAIT) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
			return;
		} else if (DsaTabPreferenceActivity.SCREEN_ORIENTATION_AUTO.equals(orientation)
				&& getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_SENSOR
				&& getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED) {
			setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
			return;
		}

		initDrawer();
		initHero();

		getLoaderManager().initLoader(LOADER_HERO_INFOS, null, new HeroesLoaderCallback(this, mDrawer));
	}

	@Override
	public void onClick(com.heinrichreimersoftware.materialdrawer.structure.DrawerItem drawerItem, long id, int position) {
		switch ((int) drawerItem.getId()) {
		case DRAWER_ID_HEROES:
			showHeroChooser();
			break;
		case DRAWER_ID_ITEMS:
			ItemsActivity.view(this);
			break;
		case DRAWER_ID_SETTINGS:
			DsaTabPreferenceActivity.startPreferenceActivity(this);
			break;
		case DRAWER_ID_TABS:
			if (getHero() != null) {
				TabEditActivity.edit(this, getHeroConfiguration().getTabs().indexOf(tabInfo), ACTION_EDIT_TABS);
			}
			break;
		default:
			showTab(position);
			break;
		}
		mDrawer.selectItem(position);
		mDrawerLayout.closeDrawer();
	}

	private DrawerProfile updateDrawerProfile(DrawerProfile drawerProfile, HeroFileInfo heroInfo) {
		if (drawerProfile == null) {
			drawerProfile = new DrawerProfile();

		}

		Drawable profileImage = null;

		if (heroInfo.getPortraitUri() != null)
			profileImage = ResUtil.getDrawableByUri(this, Uri.parse(heroInfo.getPortraitUri()));
		else
			profileImage = getResources().getDrawable(R.drawable.profile_picture);

		drawerProfile.setAvatar(profileImage).setBackground(getResources().getDrawable(R.drawable.profile_background))
				.setName(heroInfo.getName()).setDescription(heroInfo.getVersion());

		drawerProfile.setTag(heroInfo);

		BitmapDrawable bitmapProfile = null;
		if (profileImage instanceof BitmapDrawable) {
			bitmapProfile = (BitmapDrawable) profileImage;
			drawerProfile.setRoundedAvatar(bitmapProfile);
		}

		mDrawer.addProfile(drawerProfile);

		return drawerProfile;
	}

	public void setupDrawerProfile(Hero hero) {

		for (DrawerProfile profile : mDrawer.getProfiles()) {
			if (hero.getFileInfo().equals(profile.getTag())) {
				updateDrawerProfile(profile, hero.getFileInfo());
				break;
			}
		}
	}

	/**
    *
    */
	public void setupDrawerProfiles(List<HeroFileInfo> heroes) {

		mDrawer.removeOnProfileSwitchListener();
		mDrawer.clearProfiles();

		DrawerProfile currentProfile = null;
		for (HeroFileInfo heroInfo : heroes) {
			DrawerProfile profile = updateDrawerProfile(null, heroInfo);

			if (heroFileInfo != null && heroInfo.equals(heroFileInfo)) {
				currentProfile = profile;
			}
		}

		mDrawer.setOnProfileSwitchListener(this);
		mDrawer.selectProfile(currentProfile);

	}

	/**
     *
     */
	public void setupDrawerItems(Hero hero) {
		List<TabInfo> tabs;
		if (hero != null && hero.getHeroConfiguration() != null)
			tabs = hero.getHeroConfiguration().getTabs();
		else
			tabs = Collections.emptyList();

		mDrawerLayout.clearItems();

		if (tabs != null) {
			for (TabInfo tabInfo : tabs) {
				mDrawerLayout.addItem(new com.heinrichreimersoftware.materialdrawer.structure.DrawerItem().setImage(
						ResUtil.getDrawableByUri(this, tabInfo.getIconUri())).setTextPrimary(tabInfo.getTitle()));
			}
		}

		mDrawerLayout.addItem(new DrawerHeaderItem().setTitle("System"));

		mDrawerLayout.addItem(new com.heinrichreimersoftware.materialdrawer.structure.DrawerItem()
				.setImage(getResources().getDrawable(R.drawable.dsa_group)).setTextPrimary("Helden")
				.setId(DRAWER_ID_HEROES));

		if (hero != null) {
			mDrawerLayout.addItem(new com.heinrichreimersoftware.materialdrawer.structure.DrawerItem()
					.setImage(getResources().getDrawable(R.drawable.ic_menu_moreoverflow))
					.setTextPrimary("Tabs anpassen").setId(DRAWER_ID_TABS));
		}

		mDrawerLayout.addItem(new com.heinrichreimersoftware.materialdrawer.structure.DrawerItem()
				.setImage(getResources().getDrawable(R.drawable.dsa_items)).setTextPrimary("Gegenstände")
				.setId(DRAWER_ID_ITEMS));

		mDrawerLayout.addItem(new com.heinrichreimersoftware.materialdrawer.structure.DrawerItem()
				.setImage(getResources().getDrawable(R.drawable.ic_menu_preferences)).setTextPrimary("Einstellungen")
				.setId(DRAWER_ID_SETTINGS));

	}

	private void initDrawer() {
		mDrawerLayout = (DrawerFrameLayout) findViewById(R.id.drawer_layout);
		mDrawerLayout.setClickable(true);
		mDrawer = ((DrawerView) findViewById(R.id.mdDrawer));

		drawerToggle = new ActionBarDrawerToggle(this, mDrawerLayout, toolbar, R.string.drawer_open,
				R.string.drawer_close) {

			public void onDrawerClosed(View view) {
				invalidateOptionsMenu();
			}

			public void onDrawerOpened(View drawerView) {
				invalidateOptionsMenu();
			}
		};

		mDrawerLayout.setStatusBarBackgroundColor(Util.getThemeColors(this, R.attr.colorPrimaryDark));
		mDrawer.setOnItemClickListener(this);
		mDrawer.setOnProfileSwitchListener(this);
		mDrawerLayout.setDrawerListener(drawerToggle);
		mDrawerLayout.closeDrawer();

		if (DsaTabApplication.getInstance().isFirstRun()) {
			mDrawerLayout.openDrawer();
		}
	}

	@Override
	public void onSwitch(DrawerProfile oldProfile, long oldId, DrawerProfile newProfile, long newId) {

		HeroFileInfo newHeroInfo = (HeroFileInfo) newProfile.getTag();

		if (getHero() != null && getHero().getFileInfo().equals(newHeroInfo))
			return;

		mDrawer.closeProfileList();
		mDrawerLayout.closeDrawer();
		loadHero(newHeroInfo);
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		drawerToggle.onConfigurationChanged(newConfig);
	}

	@Override
	protected void onPostCreate(Bundle savedInstanceState) {
		super.onPostCreate(savedInstanceState);
		drawerToggle.syncState();
		if (tabInfo != null) {
			setActionbarTranslucent(tabInfo.isActionbarTranslucent());
		}
	}

	private void colorize(Bitmap photo) {
		Palette.generateAsync(photo, this);
	}

	@Override
	public void onGenerated(Palette palette) {
		DsaTabApplication.getInstance().setPalette(palette);
		applyPalette(palette);
	}

	@Override
	public void onLoadingComplete(String uri, View view, Bitmap bitmap) {
		colorize(bitmap);
	}

	@Override
	public void onLoadingCancelled(String arg0, View arg1) {

	}

	@Override
	public void onLoadingFailed(String arg0, View arg1, FailReason arg2) {

	}

	@Override
	public void onLoadingStarted(String arg0, View arg1) {

	}

	@SuppressLint("NewApi")
	public void applyPalette(Palette palette) {
		if (!DsaTabApplication.getPreferences().getBoolean(DsaTabPreferenceActivity.KEY_USE_PALETTE, false))
			return;

		super.applyPalette(palette);

		// getWindow().setBackgroundDrawable(new ColorDrawable(palette.getDarkMutedColor().getRgb()));
		//
		// TextView titleView = (TextView) findViewById(R.id.title);
		// titleView.setTextColor(palette.getVibrantColor().getRgb());
		//
		// TextView descriptionView = (TextView) findViewById(R.id.description);
		// descriptionView.setTextColor(palette.getLightVibrantColor().getRgb());
		//
		// colorRipple(R.id.info, palette.getDarkMutedColor().getRgb(), palette.getDarkVibrantColor().getRgb());
		// colorRipple(R.id.star, palette.getMutedColor().getRgb(), palette.getVibrantColor().getRgb());
		//
		// View infoView = findViewById(R.id.information_container);
		// infoView.setBackgroundColor(palette.getLightMutedColor().getRgb());
		//
		// AnimatedPathView star = (AnimatedPathView) findViewById(R.id.star_container);
		// star.setFillColor(palette.getVibrantColor().getRgb());
		// star.setStrokeColor(palette.getLightVibrantColor().getRgb());

		if (diceSliderFragment != null && diceSliderFragment.isAdded()) {
			diceSliderFragment.applyPalette(palette);
		}

		for (int i = 0; i < TabInfo.MAX_TABS_PER_PAGE; i++) {
			if (getCurrentFragment(i) != null && getCurrentFragment(i) instanceof BaseFragment) {
				((BaseFragment) getCurrentFragment(i)).applyPalette(palette);
			}
		}
	}

	private HeroConfiguration getHeroConfiguration() {
		HeroConfiguration tabConfig = null;
		if (getHero() != null) {
			tabConfig = getHero().getHeroConfiguration();
		}
		return tabConfig;
	}

	public void notifyTabsChanged(TabInfo oldTabInfo) {
		setupDrawerItems(getHero());
		showTab(refreshTabInfo(0), true);
	}

	/**
	 * 
	 */
	private TabInfo refreshTabInfo(int defaultTabIndex) {

		if (getHeroConfiguration() == null || getHeroConfiguration().getTabs().isEmpty()) {
			tabInfo = null;
			return null;
		}

		List<TabInfo> tabs = getHeroConfiguration().getTabs();

		// if we have an existing tabinfo check if it's upto date
		if (tabInfo != null) {
			// check wether tabinfo is uptodate (within current tabconfig
			if (tabs.contains(tabInfo)) {
				return tabInfo;
			}

			// look for tabinfo with same activities
			for (TabInfo tab : tabs) {

				if (Util.equalsOrNull(tab.getActivityClazz(0), tabInfo.getActivityClazz(0))
						&& Util.equalsOrNull(tab.getActivityClazz(1), tabInfo.getActivityClazz(1))) {
					return tab;
				}
			}

			// if we have portraitmode and a secondary clazz this means we
			// switched from landscape here, in this case we look for a tabinfo
			// with the primary activityclass and use this one
			if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT
					&& tabInfo.getActivityClazz(1) != null) {

				Class<? extends BaseFragment> activityClazz = tabInfo.getActivityClazz(0);
				if (activityClazz != null) {
					for (TabInfo tab : tabs) {
						if (activityClazz.equals(tab.getActivityClazz(0))) {
							return tab;
						}
					}
				}
			}

			// if we have landscape mode and a empty secondary activity look for
			// one with one
			if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE
					&& tabInfo.getActivityClazz(1) == null) {

				Class<? extends BaseFragment> activityClazz = tabInfo.getActivityClazz(0);
				if (activityClazz != null) {
					for (TabInfo tab : tabs) {
						if (activityClazz.equals(tab.getActivityClazz(0))
								|| activityClazz.equals(tab.getActivityClazz(1))) {
							return tab;

						}
					}
				}
			}
		}

		// last resort set tabinfo to first one if no matching one is found
		if (defaultTabIndex >= 0 && defaultTabIndex < tabs.size())
			return tabs.get(defaultTabIndex);
		else
			return tabs.get(0);
	}

	public boolean showTab(int index) {
		if (getHeroConfiguration() != null && index >= 0 && index < getHeroConfiguration().getTabs().size()) {
			return showTab(getHeroConfiguration().getTab(index), false);
		} else {
			return false;
		}
	}

	protected boolean isUpdateRequired(TabInfo tabInfo) {

		if (tabInfo != this.tabInfo)
			return true;

		boolean dirty = false;
		for (int i = 0; i < TabInfo.MAX_TABS_PER_PAGE; i++) {
			int containerId = containerIds[i];
			Fragment fragment = getFragmentManager().findFragmentById(containerId);

			if (fragment == null && tabInfo.getActivityClazz(i) == null) {
				continue;
			}

			if (fragment != null) {
				dirty = !fragment.getClass().equals(tabInfo.getActivityClazz(i));

				if (fragment instanceof BaseFragment) {
					BaseFragment baseFragment = (BaseFragment) fragment;
					dirty = !baseFragment.getTabInfo().equals(tabInfo);
				}
			}

			if (tabInfo.getActivityClazz(i) != null) {
				if (fragment != null)
					dirty = !tabInfo.getActivityClazz(i).equals(fragment.getClass());
				else
					dirty = true;
			}

		}

		dirty |= tabInfo.isDiceSlider() != diceSliderFragment.isVisible();

		return dirty;
	}

	protected boolean showTab(TabInfo newTabInfo, boolean forceRefresh) {

		if (newTabInfo != null && getHeroConfiguration() != null && (forceRefresh || isUpdateRequired(newTabInfo))) {

			this.tabInfo = newTabInfo;
			String tag = newTabInfo.getId().toString();

			FragmentTransaction ft = getFragmentManager().beginTransaction();
			ft.setCustomAnimations(android.R.animator.fade_in, android.R.animator.fade_out);

			for (int i = 0; i < TabInfo.MAX_TABS_PER_PAGE; i++) {
				int containerId = containerIds[i];
				ViewGroup fragmentContainer = containers.get(i);

				// detach old fragment
				Fragment oldFragment = getFragmentManager().findFragmentById(containerId);
				if (oldFragment != null) {
					ft.detach(oldFragment);
				}

				Fragment fragment = getFragmentManager().findFragmentByTag(tag + i);

				// check wether fragment is still uptodate, if we changed the tpe of the fragment the manager has an old
				// fragment,remove it if the class does not match
				if (fragment != null) {
					if (fragment.getClass() != tabInfo.getActivityClazz(i)) {
						ft.remove(fragment);
						fragment = null;
					}
				}

				if (fragment == null) {
					fragment = BaseFragment.newInstance(newTabInfo.getActivityClazz(i), newTabInfo, i);
					if (fragment != null) {
						Debug.verbose("Creating new fragment and adding it" + tag + " " + fragment);

						fragmentContainer.setVisibility(View.VISIBLE);
						ft.add(containerId, fragment, tag + i);
					} else {
						fragmentContainer.setVisibility(View.GONE);
					}
				} else {
					Debug.verbose("Reusing fragment " + tag + " " + fragment);
					fragmentContainer.setVisibility(View.VISIBLE);
					ft.attach(fragment);
				}

			}
			updatePage(tabInfo, ft);
			ft.commitAllowingStateLoss();
			onFragmentsUpdated();
			return true;
		} else {
			return false;
		}
	}

	private void onFragmentsUpdated() {
		setActionbarTranslucent(tabInfo.isActionbarTranslucent());
	}

	public void replaceFragment(String tag, Fragment oldFragment, Fragment newFragment) {
		FragmentTransaction ft = getFragmentManager().beginTransaction();

		ViewGroup fragmentContainer = null;
		int containerId = 0;
		for (int i = 0; i < TabInfo.MAX_TABS_PER_PAGE; i++) {
			containerId = containerIds[i];
			fragmentContainer = containers.get(i);

			// detach old fragment
			Fragment fragment = getFragmentManager().findFragmentById(containerId);
			if (fragment != null && oldFragment == fragment) {
				ft.detach(oldFragment);
				break;
			}
		}

		if (newFragment != null) {
			fragmentContainer.setVisibility(View.VISIBLE);
			ft.add(containerId, newFragment, tag);
		} else {
			fragmentContainer.setVisibility(View.GONE);
		}

		ft.setTransition(FragmentTransaction.TRANSIT_FRAGMENT_OPEN);
		ft.addToBackStack(tag);

		updatePage(null, ft);
		ft.commitAllowingStateLoss();
		onFragmentsUpdated();
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onBackPressed()
	 */
	@Override
	public void onBackPressed() {

		boolean drawerOpen = mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mDrawer);
		boolean sliderOpen = diceSliderFragment != null && diceSliderFragment.isVisible()
				&& diceSliderFragment.isPanelExpanded();

		if (sliderOpen || drawerOpen) {

			if (sliderOpen)
				diceSliderFragment.collapsePanel();

			if (drawerOpen)
				mDrawerLayout.closeDrawer();
		} else {
			if (backPressed + 2000 > System.currentTimeMillis() || getFragmentManager().getBackStackEntryCount() > 0) {
				super.onBackPressed();
			} else {
				Toast.makeText(getBaseContext(), "Erneut klicken um DsaTab zu schließen", Toast.LENGTH_SHORT).show();
				backPressed = System.currentTimeMillis();
			}

		}
	}

	protected void onHeroLoaded(Hero hero) {

		if (hero == null) {
			Toast.makeText(this, R.string.message_load_hero_failed, Toast.LENGTH_LONG).show();
			return;
		}

		int defaultTabIndex = preferences.getInt(TAB_INDEX, 0);

		setupDrawerItems(hero);
		showTab(refreshTabInfo(defaultTabIndex), true);

		if (diceSliderFragment != null && diceSliderFragment.isAdded()) {
			diceSliderFragment.loadHero(hero);
		}
	}

	public boolean checkProbe(AbstractBeing being, Probe probe) {
		return checkProbe(being, probe, true);
	}

	public boolean checkProbe(AbstractBeing being, Probe probe, boolean autoRoll) {
		if (diceSliderFragment != null) {
			if (probe != null && being != null) {
				diceSliderFragment.checkProbe(being, probe, autoRoll);
				return true;
			}
		}
		return false;
	}

	private void unregisterShakeDice() {
		try {
			if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
				if (mShaker != null) {
					mShaker.setOnShakeListener(null);
					mShaker = null;
				}
			}
		} catch (UnsupportedOperationException e) {
			mShaker = null;
			Debug.warning(e);
		}
	}

	private void registerShakeDice() {
		try {
			if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
				if (mShaker == null) {
					final Vibrator vibe = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
					mShaker = new ShakeListener(this);
					mShaker.setOnShakeListener(new ShakeListener.OnShakeListener() {
						@Override
						public void onShake() {
							vibe.vibrate(100);
							if (diceSliderFragment != null) {
								diceSliderFragment.expandPanel();
								diceSliderFragment.rollDice20();
							}
						}
					});

				}
			}
		} catch (UnsupportedOperationException e) {
			mShaker = null;
			Debug.warning(e);
		}
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onDestroy()
	 */
	@Override
	protected void onDestroy() {
		unregisterShakeDice();

		DsaTabApplication.getPreferences().unregisterOnSharedPreferenceChangeListener(this);

		if (getHero() != null && preferences.getBoolean(DsaTabPreferenceActivity.KEY_AUTO_SAVE, true)) {
			HeroSaveTask heroSaveTask = new HeroSaveTask(this, getHero());
			heroSaveTask.execute();
		}

		if (getHeroConfiguration() != null && getHeroConfiguration().getTabs() != null) {
			Editor edit = preferences.edit();

			int tabIndex = getHeroConfiguration().getTabs().indexOf(tabInfo);
			edit.putInt(TAB_INDEX, tabIndex);
			edit.commit();
		}

		super.onDestroy();
	}

	@Override
	protected void onPause() {
		try {
			if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
				if (mShaker != null)
					mShaker.pause();
			}
		} catch (UnsupportedOperationException e) {
			mShaker = null;
			Debug.warning(e);
		}

		super.onPause();
	}

	@Override
	protected void onResume() {
		try {
			if (getPackageManager().hasSystemFeature(PackageManager.FEATURE_SENSOR_ACCELEROMETER)) {
				if (preferences.getBoolean(DsaTabPreferenceActivity.KEY_PROBE_SHAKE_ROLL_DICE, false)) {
					if (mShaker == null)
						registerShakeDice();
					else
						mShaker.resume();
				}
			}
		} catch (UnsupportedOperationException e) {
			mShaker = null;
			Debug.warning(e);
		}

		DsaTabApplication.getInstance().showNewsInfoPopup(this);

		super.onResume();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();

		inflater.inflate(R.menu.menuitem_search, menu);

		MenuItem searchMenuItem = menu.findItem(R.id.action_search);
		if (searchMenuItem != null) {
			android.support.v7.widget.SearchView searchViewAction = (android.support.v7.widget.SearchView) MenuItemCompat
					.getActionView(searchMenuItem);
			if (searchViewAction != null) {
				SearchManager manager = (SearchManager) getSystemService(Context.SEARCH_SERVICE);
				searchViewAction.setSearchableInfo(manager.getSearchableInfo(getComponentName()));
				searchViewAction.setIconifiedByDefault(true);
			}
		}
		inflater.inflate(R.menu.menuitem_savehero, menu);

		return super.onCreateOptionsMenu(menu);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see com.actionbarsherlock.app.SherlockFragmentActivity#onPrepareOptionsMenu (com.actionbarsherlock.view.Menu)
	 */
	@Override
	public boolean onPrepareOptionsMenu(Menu menu) {
		MenuItem item = menu.findItem(R.id.option_set);
		if (item != null) {
			item.setEnabled(getHero() != null);
			if (getHero() != null) {
				switch (getHero().getActiveSet()) {
				case 0:
					item.setIcon(Util.getThemeResourceId(this, R.attr.imgBarSet1));
					if (menu.findItem(R.id.option_set1) != null) {
						menu.findItem(R.id.option_set1).setChecked(true);
					}
					break;
				case 1:
					item.setIcon(Util.getThemeResourceId(this, R.attr.imgBarSet2));
					if (menu.findItem(R.id.option_set2) != null) {
						menu.findItem(R.id.option_set2).setChecked(true);
					}
					break;
				case 2:
					item.setIcon(Util.getThemeResourceId(this, R.attr.imgBarSet3));
					if (menu.findItem(R.id.option_set3) != null) {
						menu.findItem(R.id.option_set3).setChecked(true);
					}
					break;
				}
			}
		}

		item = menu.findItem(R.id.option_save_hero);
		if (item != null) {
			item.setVisible(!preferences.getBoolean(DsaTabPreferenceActivity.KEY_AUTO_SAVE, true));
			item.setEnabled(getHero() != null);
		}

		boolean result = super.onPrepareOptionsMenu(menu);

		// hide all actions if drawer is opened (android design guideline)
		if (isDrawerOpened()) {
			for (int i = 0; i < menu.size(); i++) {
				menu.getItem(i).setVisible(false);
			}
		}

		return result;
	}

	public boolean isDrawerOpened() {
		return mDrawerLayout != null && mDrawerLayout.isDrawerOpen(mDrawer);
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {

		// Pass the event to ActionBarDrawerToggle, if it returns
		// true, then it has handled the app icon touch event
		if (drawerToggle.onOptionsItemSelected(item)) {
			return true;
		}

		switch (item.getItemId()) {
		case R.id.option_save_hero:
			if (getHero() != null) {
				HeroSaveTask heroSaveTask = new HeroSaveTask(this, getHero());
				heroSaveTask.execute();
			}
			return true;
		case R.id.option_set1:
			if (getHero() != null) {
				getHero().setActiveSet(0);
				item.setChecked(true);
			}
			return true;
		case R.id.option_set2:
			if (getHero() != null) {
				getHero().setActiveSet(1);
				item.setChecked(true);
			}
			return true;
		case R.id.option_set3:
			if (getHero() != null) {
				getHero().setActiveSet(2);
				item.setChecked(true);
			}
			return true;
		}

		return super.onOptionsItemSelected(item);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.FragmentActivity#onSaveInstanceState(android.os .Bundle)
	 */
	@Override
	protected void onSaveInstanceState(Bundle outState) {
		// super.onSaveInstanceState(outState);
		outState.putParcelable(KEY_TAB_INFO, tabInfo);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onRestoreInstanceState(android.os.Bundle)
	 */
	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		// super.onRestoreInstanceState(savedInstanceState);
		if (savedInstanceState.containsKey(KEY_TAB_INFO))
			tabInfo = savedInstanceState.getParcelable(KEY_TAB_INFO);
	}

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.content.SharedPreferences.OnSharedPreferenceChangeListener#
	 * onSharedPreferenceChanged(android.content.SharedPreferences, java.lang.String)
	 */
	@Override
	public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
		// Debug.verbose(key + " changed");

		if (DsaTabPreferenceActivity.KEY_STYLE_BG_PATH.equals(key)) {
			applyPreferencesToTheme();
		}

		if (DsaTabPreferenceActivity.KEY_SCREEN_ORIENTATION.equals(key)) {
			String orientation = sharedPreferences.getString(DsaTabPreferenceActivity.KEY_SCREEN_ORIENTATION,
					DsaTabPreferenceActivity.DEFAULT_SCREEN_ORIENTATION);

			if (DsaTabPreferenceActivity.SCREEN_ORIENTATION_LANDSCAPE.equals(orientation)) {
				if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE) {
					// You need to check if your desired orientation isn't
					// already set because setting orientation restarts your
					// Activity which takes long
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
				}
			} else if (DsaTabPreferenceActivity.SCREEN_ORIENTATION_PORTRAIT.equals(orientation)) {
				if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_PORTRAIT) {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
				}
			} else if (DsaTabPreferenceActivity.SCREEN_ORIENTATION_AUTO.equals(orientation)) {
				if (getRequestedOrientation() != ActivityInfo.SCREEN_ORIENTATION_SENSOR) {
					setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_SENSOR);
				}
			}
		}

		if (DsaTabPreferenceActivity.KEY_FULLSCREEN.equals(key)) {
			updateFullscreenStatus(preferences.getBoolean(DsaTabPreferenceActivity.KEY_FULLSCREEN, false));
		}

		// notify other listeners (fragments, heroes)
		for (int i = 0; i < TabInfo.MAX_TABS_PER_PAGE; i++) {
			if (getCurrentFragment(i) instanceof OnSharedPreferenceChangeListener) {
				((OnSharedPreferenceChangeListener) getCurrentFragment(i)).onSharedPreferenceChanged(sharedPreferences,
						key);
			}
		}

		if (diceSliderFragment != null && diceSliderFragment.isAdded())
			diceSliderFragment.onSharedPreferenceChanged(sharedPreferences, key);
	}

	protected void showHeroChooser() {
		Intent intent = new Intent(this, HeroChooserActivity.class);
		startActivityForResult(intent, ACTION_CHOOSE_HERO);
	}

}
