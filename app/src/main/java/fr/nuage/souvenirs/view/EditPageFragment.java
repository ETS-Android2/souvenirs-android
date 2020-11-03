package fr.nuage.souvenirs.view;

import android.app.Activity;
import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.GestureDetector;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.appcompat.widget.Toolbar;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.view.GestureDetectorCompat;
import androidx.databinding.DataBindingUtil;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.appbar.AppBarLayout;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.UUID;

import fr.nuage.souvenirs.R;
import fr.nuage.souvenirs.databinding.FragmentEditPageBinding;
import fr.nuage.souvenirs.model.PageBuilder;
import fr.nuage.souvenirs.view.helpers.ElementMoveDragListener;
import fr.nuage.souvenirs.view.helpers.ViewGenerator;
import fr.nuage.souvenirs.viewmodel.AlbumListViewModel;
import fr.nuage.souvenirs.viewmodel.AlbumListViewModelFactory;
import fr.nuage.souvenirs.viewmodel.AlbumViewModel;
import fr.nuage.souvenirs.viewmodel.ElementViewModel;
import fr.nuage.souvenirs.viewmodel.ImageElementViewModel;
import fr.nuage.souvenirs.viewmodel.PageViewModel;
import fr.nuage.souvenirs.viewmodel.PaintElementViewModel;
import fr.nuage.souvenirs.viewmodel.TextElementViewModel;

public class EditPageFragment extends Fragment  {

    private static final int ACTIVITY_ADD_IMAGE = 10;

    private static final String DIALOG_CHANGE_STYLE_PAGE = "DIALOG_CHANGE_STYLE_PAGE";

    private PageViewModel pageVM;
    private int activityScrollStatus;
    private ElementViewModel actionModeElement = null;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        //load album path in args
        String albumPath = EditPageFragmentArgs.fromBundle(getArguments()).getAlbumPath();
        String pageId = EditPageFragmentArgs.fromBundle(getArguments()).getPageId();

        //load view model
        AlbumViewModel albumVM = new ViewModelProvider(getActivity(),new AlbumListViewModelFactory(getActivity().getApplication())).get(AlbumListViewModel.class).getAlbum(albumPath);
        pageVM = albumVM.getPage(UUID.fromString(pageId));
        //set focus on that page
        albumVM.setFocusPage(pageVM);

        setHasOptionsMenu(true);
    }

    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        //set title
        getActivity().setTitle(R.string.edit_page_title);

        //inflateview
        FragmentEditPageBinding binding = DataBindingUtil.inflate(inflater, R.layout.fragment_edit_page, container, false);
        binding.setFragment(this);
        binding.setPage(pageVM);
        ConstraintLayout pageLayout = binding.pageLayout;

        EditPageFragment that = this;

        //listen to elements changes
        pageVM.getElements().observe(getViewLifecycleOwner(), elementViewModels -> {
            //remove all
            pageLayout.removeAllViewsInLayout();
            //rebuild layout
            if (elementViewModels != null) {
                LayoutInflater inflater1 = LayoutInflater.from(pageLayout.getContext());
                for (ElementViewModel e : elementViewModels) {
                    if (e.getClass() == TextElementViewModel.class) {
                        TextElementViewModel et = (TextElementViewModel) e;
                        View textView = ViewGenerator.generateView(et,pageLayout,getActivity());
                        //set on click listener
                        GestureDetectorCompat elementGestureDetector = new GestureDetectorCompat(getContext(), new GestureDetector.SimpleOnGestureListener(){
                            @Override
                            public boolean onDoubleTap (MotionEvent e) {
                                EditTextElementDialogFragment.newInstance(et).show(getParentFragmentManager(),"");
                                return true;
                            }
                        });
                        ElementMoveDragListener elementMoveDragListener = new ElementMoveDragListener(pageVM,e,elementGestureDetector);
                        pageVM.getPaintMode().observe(getViewLifecycleOwner(),isPaintMode -> {
                            if (isPaintMode) {
                                textView.setOnClickListener(null);
                                textView.setOnTouchListener(null);
                                textView.setOnLongClickListener(null);
                                textView.setOnDragListener(null);
                            } else {
                                textView.setOnClickListener(elementMoveDragListener);
                                textView.setOnTouchListener(elementMoveDragListener);
                                textView.setOnLongClickListener(elementMoveDragListener);
                                textView.setOnDragListener(elementMoveDragListener);
                            }
                        });
                        //subscribe to selection
                        e.getIsSelected().observe(getViewLifecycleOwner(),(isSelected)-> {
                            if (isSelected && ((actionModeElement==null) || !actionModeElement.equals(e))) {
                                getActivity().startActionMode(new TextActionModeCallback((TextElementViewModel) e));
                                actionModeElement = e;
                            }
                        });
                    } else if (e.getClass() == ImageElementViewModel.class) {
                        ImageElementViewModel ei = (ImageElementViewModel) e;
                        View imageView = ViewGenerator.generateView(ei,pageLayout, that);
                        ElementMoveDragListener elementMoveDragListener = new ElementMoveDragListener(pageVM, ei);
                        pageVM.getPaintMode().observe(getViewLifecycleOwner(),isPaintMode -> {
                            if (isPaintMode) {
                                imageView.setOnClickListener(null);
                                imageView.setOnTouchListener(null);
                                imageView.setOnLongClickListener(null);
                                imageView.setOnDragListener(null);
                            } else {
                                imageView.setOnClickListener(elementMoveDragListener);
                                imageView.setOnTouchListener(elementMoveDragListener);
                                imageView.setOnLongClickListener(elementMoveDragListener);
                                imageView.setOnDragListener(elementMoveDragListener);
                            }
                        });
                        //subscribe to selection
                        ei.getIsSelected().observe(getViewLifecycleOwner(),(isSelected)-> {
                            if (isSelected && ((actionModeElement==null) || (!actionModeElement.equals(ei)))) {
                                getActivity().startActionMode(new ImageActionModeCallback(ei));
                                actionModeElement = ei;
                            }
                        });
                    } else if (e.getClass() == PaintElementViewModel.class) {
                        PaintElementViewModel ep = (PaintElementViewModel) e;
                        PaintElementView paintView = ViewGenerator.generateView(ep,pageLayout,getActivity());
                        //listen to paint mode
                        pageVM.getPaintMode().observe(getViewLifecycleOwner(),isPaintMode -> {
                            if (isPaintMode) {
                                //activate submenu
                                getActivity().startActionMode(new PaintActionModeCallback(getActivity().getSupportFragmentManager(),pageVM,ep));
                            }
                            //activate/deactivate draw on page
                            paintView.setPaintMode(isPaintMode);
                        });
                        //listen to color change
                        ep.getLdColor().observe(getViewLifecycleOwner(), paintView::setColor);
                    } else {
                        //unknown element : display default view
                        inflater1.inflate(R.layout.unknown_element_view, pageLayout, true);
                        ImageView unknownImage = pageLayout.findViewById(R.id.unknown_imageview);
                    }
                }
            }
        });

        return binding.getRoot();
    }

    @Override
    public void onStart() {
        super.onStart();
        //dirty trick to disable toolbar scrolling for this fragment only
        this.activityScrollStatus = ((AppBarLayout.LayoutParams)((Toolbar) getActivity().findViewById(R.id.toolbar)).getLayoutParams()).getScrollFlags();
        ((AppBarLayout.LayoutParams)((Toolbar) getActivity().findViewById(R.id.toolbar)).getLayoutParams()).setScrollFlags(0);
    }

    @Override
    public void onStop() {
        //restore activity scrolling
        ((AppBarLayout.LayoutParams)((Toolbar) getActivity().findViewById(R.id.toolbar)).getLayoutParams()).setScrollFlags(this.activityScrollStatus);
        super.onStop();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.menu_edit_page, menu);

        //set logic to add image
        MenuItem addImageItem = menu.findItem(R.id.edit_page_add_image);
        addImageItem.setOnMenuItemClickListener(menuItem -> {
            //open image picker
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.addCategory(Intent.CATEGORY_OPENABLE);
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            startActivityForResult(intent, ACTIVITY_ADD_IMAGE);
            return true;
        });

        //set logic to add text
        MenuItem addTextItem = menu.findItem(R.id.edit_page_add_text);
        addTextItem.setOnMenuItemClickListener(menuItem -> {
            pageVM.addText();
            return true;
        });

        //set logic draw mode
        MenuItem drawItem = menu.findItem(R.id.edit_page_draw);
        drawItem.setOnMenuItemClickListener(menuItem -> {
            //start paint mode
            pageVM.startPaintMode();
            return true;
        });

        //set logic to change style
        MenuItem changeStyle = menu.findItem(R.id.edit_page_change_style);
        changeStyle.setOnMenuItemClickListener(menuItem -> {
            SelectPageStyleFragment.OnSelectPageStyleListener selectPageStyleListener = style -> PageBuilder.applyStyle(style,pageVM.getPage());
            //launch select style dialog
            SelectPageStyleDialogFragment dialog = SelectPageStyleDialogFragment.newInstance(selectPageStyleListener,pageVM.getNbImage(),pageVM.getNbText());
            dialog.show(getFragmentManager(),DIALOG_CHANGE_STYLE_PAGE);
            return true;
        });

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case ACTIVITY_ADD_IMAGE:
                if (resultCode == Activity.RESULT_OK) {
                    ArrayList<Uri> uris = new ArrayList<Uri>();
                    ClipData clipdata = data.getClipData();
                    if (clipdata == null) {
                        uris.add(data.getData());
                    } else {
                        //handle multiple uri
                        for (int i = 0; i < clipdata.getItemCount(); i++) {
                            uris.add(clipdata.getItemAt(i).getUri());
                        }
                    }
                    for (Uri uri: uris) {
                        InputStream input = PageBuilder.getInputStreamFromUri(getActivity().getContentResolver(), uri);
                        String mime = getActivity().getContentResolver().getType(uri);
                        pageVM.addImage(input,mime);
                    }
                }
                break;
        }
    }


}
