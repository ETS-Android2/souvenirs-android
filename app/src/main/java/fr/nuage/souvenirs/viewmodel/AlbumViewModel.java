package fr.nuage.souvenirs.viewmodel;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.os.Environment;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;
import androidx.navigation.Navigation;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.Locale;
import java.util.UUID;

import fr.nuage.souvenirs.R;
import fr.nuage.souvenirs.SyncService;
import fr.nuage.souvenirs.model.Album;
import fr.nuage.souvenirs.model.Page;
import fr.nuage.souvenirs.model.PageBuilder;
import fr.nuage.souvenirs.model.TilePageBuilder;
import fr.nuage.souvenirs.model.nc.AlbumNC;
import fr.nuage.souvenirs.model.nc.AlbumsNC;
import fr.nuage.souvenirs.view.ShowAlbumFragmentDirections;

public class AlbumViewModel extends AndroidViewModel {

    public static final int NC_STATE_NONE = 0;
    public static final int NC_STATE_UNKNOWN = 1;
    public static final int NC_STATE_ERROR = 2;
    public static final int NC_STATE_SYNC = 3;
    public static final int NC_STATE_NOSYNC = 4;
    public static final int NC_STATE_SYNC_IN_PROGRESS = 5;

    private MediatorLiveData<String> name = new MediatorLiveData<>();
    private Album album;
    private AlbumNC albumNC;
    private UUID id;
    private LiveData<ArrayList<PageViewModel>> pages = new MutableLiveData<>();
    private MediatorLiveData<String> ldDate = new MediatorLiveData<>();
    private MediatorLiveData<String> ldAlbumImage = new MediatorLiveData<>();
    private MutableLiveData<UUID> focusPageId = new MutableLiveData<>();
    private MutableLiveData<Boolean> ldHasAlbumNC = new MutableLiveData<>();
    private MutableLiveData<Boolean> ldHasAlbum = new MutableLiveData<>();
    private boolean syncInProgress = false;
    private MediatorLiveData<Boolean> ldIsShared = new MediatorLiveData<>();
    private MediatorLiveData<Integer> ldNCState = new MediatorLiveData<>();
    private MediatorLiveData<String> ldDefaultStyle = new MediatorLiveData<>();
    private int albumsNCState = AlbumsNC.STATE_NOT_LOADED;

    public AlbumViewModel(Application app) {
        super(app);
        ldNCState.postValue(NC_STATE_NONE);
    }

    public AlbumViewModel(Application app, @NonNull AlbumNC a) {
        this(app);
        setAlbumNC(a);
        id = a.getId();
    }

    public AlbumViewModel(Application app, @NonNull Album a) {
        this(app);
        setAlbum(a);
        id = a.getId();
    }

    public boolean hasLocalAlbum() {
        return (album != null);
    }

    public Album getAlbum() {
        return this.album;
    }

    public boolean hasNCAlbum() {
        return (albumNC != null);
    }

    public AlbumNC getAlbumNC() {
        return this.albumNC;
    }

    public void setAlbum(Album album) {
        Album oldAlbum = getAlbum();
        this.album = album;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if (album != oldAlbum) {
                    if (oldAlbum != null) {
                        //remove previous livedata observers for Album
                        name.removeSource(oldAlbum.getLiveDataName());
                        ldDate.removeSource(oldAlbum.getLdDate());
                        pages = new MutableLiveData<>();
                        ldAlbumImage.removeSource(oldAlbum.getLdAlbumImage());
                        ldDefaultStyle.removeSource(oldAlbum.getLdDefaultStyle());
                        ldNCState.removeSource(oldAlbum.getLdLastEditDate());
                        ldNCState.removeSource(oldAlbum.getLdPageLastEditDate());
                    }
                    if (album != null) {
                        ldHasAlbum.postValue(true);
                        //set livedata observers for Album
                        name.addSource(album.getLiveDataName(), name2 -> {
                            name.setValue(name2);
                        });
                        ldDate.addSource(album.getLdDate(), date -> {
                            ldDate.setValue((new SimpleDateFormat("dd MMMM yyyy", Locale.FRANCE)).format(date));
                        });
                        pages = Transformations.map(album.getLiveDataPages(), pages -> {
                            ArrayList<PageViewModel> out = new ArrayList<PageViewModel>();
                            for (Page p : pages) {
                                PageViewModel pVM = new PageViewModel(p);
                                out.add(pVM);
                            }
                            return out;
                        });
                        ldAlbumImage.addSource(album.getLdAlbumImage(), imagePath -> {
                            ldAlbumImage.setValue(imagePath);
                        });
                        ldDefaultStyle.addSource(album.getLdDefaultStyle(), style -> {
                            ldDefaultStyle.setValue(style);
                        });
                        ldNCState.addSource(album.getLdLastEditDate(), date -> {
                            ldNCState.postValue(getNCState());
                        });
                        ldNCState.addSource(album.getLdPageLastEditDate(), date -> {
                            ldNCState.postValue(getNCState());
                        });
                    } else {
                        ldHasAlbum.postValue(false);
                        if (albumNC != null) { //reset date to trigger ldIsSyncNC
                            albumNC.setLastEditDate(albumNC.getLastEditDate());
                        }
                    }
                }
            }
        });
    }

    public void setAlbumNC(AlbumNC albumNC) {
        AlbumNC oldAlbum = getAlbumNC();
        this.albumNC = albumNC;
        ldNCState.postValue(NC_STATE_UNKNOWN);
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                if ((albumNC != oldAlbum) && (oldAlbum != null)) {
                    //remove previous livedata observers for Album
                    name.removeSource(oldAlbum.getLdName());
                    ldDate.removeSource(oldAlbum.getLdDate());
                    ldNCState.removeSource(oldAlbum.getLdLastEditDate());
                    ldNCState.removeSource(oldAlbum.getLdPageLastEditDate());
                    ldNCState.removeSource(oldAlbum.getLdState());
                    ldIsShared.removeSource(oldAlbum.getLdIsShared());
                }
                if (albumNC != null) {
                    ldHasAlbumNC.postValue(true);
                    //set livedata observers for AlbumNC
                    name.addSource(albumNC.getLdName(), name2 -> {
                        if (album == null) {
                            name.setValue(name2);
                        }
                    });
                    ldDate.addSource(albumNC.getLdDate(), date -> {
                        if (album == null) {
                            ldDate.setValue((new SimpleDateFormat("dd MMMM yyyy", Locale.FRANCE)).format(date));
                        }
                    });
                    ldNCState.addSource(albumNC.getLdLastEditDate(), date -> {
                        ldNCState.postValue(getNCState());
                    });
                    ldNCState.addSource(albumNC.getLdPageLastEditDate(), date -> {
                        ldNCState.postValue(getNCState());
                    });
                    ldNCState.addSource(albumNC.getLdState(), state -> {
                        ldNCState.postValue(getNCState());
                    });
                    ldIsShared.addSource(albumNC.getLdIsShared(), isShared -> {
                        ldIsShared.setValue(isShared);
                    });
                } else {
                    ldHasAlbumNC.postValue(false);
                    if (album != null) { //reset date to trigger ldIsSyncNC
                        album.setLastEditDate(album.getLastEditDate());
                    }
                }
            }
        });
    }

    public boolean isInSync() {
        if ((albumNC == null) || (album == null)) {
            return false;
        }
        if ((album.getLastEditDate().equals(albumNC.getLastEditDate())) && (album.getPagesLastEditDate().equals(albumNC.getPagesLastEditDate()))) {
            return true;
        } else {
            return false;
        }
    }

    private int getNCState() {
        if (albumsNCState == AlbumsNC.STATE_NOT_LOADED) {
            return NC_STATE_UNKNOWN;
        }
        if (albumNC == null) {
            return NC_STATE_NONE;
        }
        if (albumNC.getState() == AlbumNC.STATE_NOT_LOADED) {
            return NC_STATE_UNKNOWN;
        }
        if (albumNC.getState() == AlbumNC.STATE_ERROR) {
            return NC_STATE_ERROR;
        }
        if (album == null) {
            return NC_STATE_NOSYNC;
        }
        if (syncInProgress) {
            return NC_STATE_SYNC_IN_PROGRESS;
        }
        if ((album.getLastEditDate().equals(albumNC.getLastEditDate())) && (album.getPagesLastEditDate().equals(albumNC.getPagesLastEditDate()))) {
            return NC_STATE_SYNC;
        } else {
            return NC_STATE_NOSYNC;
        }
    }

    public LiveData<Boolean> getLdHasAlbumNC() {
        return ldHasAlbumNC;
    }

    public LiveData<Boolean> getLdHasAlbum() {
        return ldHasAlbum;
    }

    public String getAlbumPath() { return album.getAlbumPath(); }

    public LiveData<String> getName() {
        return name;
    }

    public LiveData<String> getLdDate() {
        return ldDate;
    }

    public LiveData<String> getLdAlbumImage() {
        return ldAlbumImage;
    }


    public void addPage(Page p) {
        addPage(p,album.getPages().size());
    }

    public void addPage(Page p, int addPagePosition) {
        album.addPage(p,addPagePosition);
    }

    public void addPages(ArrayList<Page> pp, int addPagePosition) {
        album.addPages(pp,addPagePosition);
    }

    public Page createPage(int position) {
        return album.createPage(position);
    }

    public void addPages(ArrayList<Page> pp) {
        addPages(pp,album.getPages().size());
    }

    /*
    get position index of page inside album.
    return -1 if page null
     */
    public int getPosition(PageViewModel page) {
        if (page == null) {
            return -1;
        }
        return album.getIndex(page.getPage());
    }

    public int getPosition(UUID pageId) {
        for (Page p : album.getPages()) {
            if (p.getId().equals(pageId)) {
                return album.getPages().indexOf(p);
            }
        }
        return 0;
    }

    public LiveData<ArrayList<PageViewModel>> getPages() {
        return pages;
    }


    public String getDataPath() { return album.getDataPath(); }

    public PageViewModel getPage(int position) {
        Page page = album.getPage(position);
        if (page == null) {
            return null;
        }
        return new PageViewModel(page);
    }

    public PageViewModel getPage(UUID id) {
        return getPage(getPosition(id));
    }

    public void movePage(UUID pageToMove, UUID pageToMoveAfter) {
        album.movePage(getPosition(pageToMove),getPosition(pageToMoveAfter)+1);
    }

    public void movePage(int from, int to) {
        album.movePage(from,to);
    }

    public void setName(String name) {
        album.setName(name);
    }

    public void update() {
        if (album != null) {
            album.load();
        }
        if (albumNC != null) {
            albumNC.load();
        }
    }

    public UUID getId() {
        return id;
    }

    public Date getDate() {
        if (album != null) {
            return album.getDate();
        }
        if (albumNC != null) {
            return albumNC.getDate();
        }
        return null;
    }

    public void switchStyle(PageViewModel page, int style) {
        //create new pages
        PageBuilder pageBuilder = (getDefaultStyle().equals(Album.STYLE_TILE)) ? new TilePageBuilder() : new PageBuilder();
        pageBuilder.switchStyle(style,this,page.getPage());
        //deleteLocalAlbum old pages
        album.delPage(getPosition(page));
    }

    public void switchImage(ImageElementViewModel eOri, ImageElementViewModel eDest) {
        String sImage = eDest.getImagePath().getValue();
        String sMimeType = eDest.getMimeType();
        eDest.setImage(eOri.getImagePath().getValue(),eOri.getMimeType());
        eOri.setImage(sImage,sMimeType);
    }

    public LiveData<UUID> getFocusPageId() {
        return focusPageId;
    }

    public void setFocusPage(PageViewModel page) {
        focusPageId.postValue(page.getId());
    }


    public void launchSync() {
        //warn user
        Context context = getApplication().getApplicationContext();
        Toast.makeText(context, context.getString(R.string.launch_sync,getName().getValue()),Toast.LENGTH_LONG).show();
        //start sync to nextcloud service
        SyncService.startSync(getApplication().getApplicationContext(),this);
    }

    public void setSyncInProgress(boolean b) {
        syncInProgress = b;
        ldNCState.postValue(getNCState());
    }

    public LiveData<Boolean> getLdIsShared() {
        return ldIsShared;
    }

    public MediatorLiveData<Integer> getLdNCState() {
        return ldNCState;
    }

    public void onAlbumsNCStateChanged(Integer state) {
        albumsNCState = state;
        if (state == AlbumsNC.STATE_ERROR) {
            ldNCState.postValue(NC_STATE_ERROR);
        } else if (state == AlbumsNC.STATE_NOT_LOADED) {
            ldNCState.postValue(NC_STATE_UNKNOWN);
        } else {
            ldNCState.postValue(getNCState());
        }
    }

    public boolean getSyncInProgress() {
        return syncInProgress;
    }

    public void setDefaultStyle(String style) {
        album.setDefaultStyle(style);
    }

    public String getDefaultStyle() {
        return album.getDefaultStyle();
    }

    public MediatorLiveData<String> getLdDefaultStyle() {
        return ldDefaultStyle;
    }

    public PageViewModel getNextPage(PageViewModel pageVM) {
        if (getPages().getValue() == null) {
            return null;
        }
        int pos = getPosition(pageVM);
        if (pos < getPages().getValue().size()-1) {
            return getPage(pos + 1);
        }
        return null;
    }

    public PageViewModel getPrevPage(PageViewModel pageVM) {
        int pos = getPosition(pageVM);
        if (pos > 0) {
            return getPage(pos - 1);
        }
        return null;
    }

    public File createEmptyDataFile(String mimeType) {
        return album.createEmptyDataFile(mimeType);
    }
}
