package org.joinmastodon.android.fragments;

import android.app.ProgressDialog;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.view.inputmethod.InputMethodManager;

import org.joinmastodon.android.MastodonApp;
import org.joinmastodon.android.R;
import org.joinmastodon.android.api.MastodonErrorResponse;
import org.joinmastodon.android.api.requests.instance.GetInstance;
import org.joinmastodon.android.api.session.AccountSessionManager;
import org.joinmastodon.android.fragments.onboarding.InstanceRulesFragment;
import org.joinmastodon.android.model.Instance;
import org.joinmastodon.android.model.catalog.CatalogInstance;
import org.joinmastodon.android.ui.InterpolatingMotionEffect;
import org.joinmastodon.android.ui.M3AlertDialogBuilder;
import org.joinmastodon.android.ui.views.SizeListenerFrameLayout;
import org.parceler.Parcels;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.net.IDN;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;

import me.grishka.appkit.Nav;
import me.grishka.appkit.api.Callback;
import me.grishka.appkit.api.ErrorResponse;
import me.grishka.appkit.fragments.AppKitFragment;
import me.grishka.appkit.imageloader.ListImageLoaderWrapper;
import me.grishka.appkit.utils.BindableViewHolder;
import me.grishka.appkit.utils.V;
import me.grishka.appkit.views.UsableRecyclerView;

public class SplashFragment extends AppKitFragment{
	private CatalogInstance chosenInstance;
	private SizeListenerFrameLayout contentView;
	private View artContainer, blueFill, greenFill;
	private InterpolatingMotionEffect motionEffect;
	private final HashMap<String, Instance> instancesCache=new HashMap<>();
	private final List<CatalogInstance> filteredData=new ArrayList<>();
	private InstancesAdapter adapter;
	protected ListImageLoaderWrapper imgLoader;
	private String loadingInstanceDomain;
	private GetInstance loadingInstanceRequest;
	private ProgressDialog instanceProgressDialog;
	private boolean isSignup;
	private String currentSearchQuery;

	public SplashFragment() {
	}

	@Override
	public void onCreate(Bundle savedInstanceState){
		super.onCreate(savedInstanceState);
		motionEffect=new InterpolatingMotionEffect(MastodonApp.context);
	}

	@Nullable
	@Override
	public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, Bundle savedInstanceState){
		contentView=(SizeListenerFrameLayout) inflater.inflate(R.layout.fragment_splash, container, false);
		contentView.findViewById(R.id.btn_get_started).setOnClickListener(this::onButtonClick);
		contentView.findViewById(R.id.btn_log_in).setOnClickListener(this::onButtonClick);

		artContainer=contentView.findViewById(R.id.art_container);
		blueFill=contentView.findViewById(R.id.blue_fill);
		greenFill=contentView.findViewById(R.id.green_fill);
		motionEffect.addViewEffect(new InterpolatingMotionEffect.ViewEffect(contentView.findViewById(R.id.art_clouds), V.dp(-5), V.dp(5), V.dp(-5), V.dp(5)));
		motionEffect.addViewEffect(new InterpolatingMotionEffect.ViewEffect(contentView.findViewById(R.id.art_right_hill), V.dp(-15), V.dp(25), V.dp(-10), V.dp(10)));
		motionEffect.addViewEffect(new InterpolatingMotionEffect.ViewEffect(contentView.findViewById(R.id.art_left_hill), V.dp(-25), V.dp(15), V.dp(-15), V.dp(15)));
		motionEffect.addViewEffect(new InterpolatingMotionEffect.ViewEffect(contentView.findViewById(R.id.art_center_hill), V.dp(-14), V.dp(14), V.dp(-5), V.dp(25)));
		motionEffect.addViewEffect(new InterpolatingMotionEffect.ViewEffect(contentView.findViewById(R.id.art_plane_elephant), V.dp(-20), V.dp(12), V.dp(-20), V.dp(12)));

		contentView.setSizeListener(new SizeListenerFrameLayout.OnSizeChangedListener(){
			@Override
			public void onSizeChanged(int w, int h, int oldw, int oldh){
				contentView.getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener(){
					@Override
					public boolean onPreDraw(){
						contentView.getViewTreeObserver().removeOnPreDrawListener(this);
						updateArtSize(w, h);
						return true;
					}
				});
			}
		});

		return contentView;
	}

	private void onButtonClick(View v){

		Bundle extras=new Bundle();
		extras.putBoolean("signup", v.getId()==R.id.btn_get_started);
		onNext();
//		Nav.go(getActivity(), InstanceCatalogFragment.class, extras);
	}

	private void updateArtSize(int w, int h){
		float scale=w/(float)V.dp(412);
		artContainer.setScaleX(scale);
		artContainer.setScaleY(scale);
		blueFill.setScaleY(h/2f);
		greenFill.setScaleY(h-artContainer.getBottom()+V.dp(90));
	}


	@Override
	public void onApplyWindowInsets(WindowInsets insets){
		super.onApplyWindowInsets(insets);
		int bottomInset=insets.getSystemWindowInsetBottom();
		if(bottomInset>0 && bottomInset<V.dp(36)){
			contentView.setPadding(contentView.getPaddingLeft(), contentView.getPaddingTop(), contentView.getPaddingRight(), V.dp(36));
		}
		((ViewGroup.MarginLayoutParams)blueFill.getLayoutParams()).topMargin=-contentView.getPaddingTop();
		((ViewGroup.MarginLayoutParams)greenFill.getLayoutParams()).bottomMargin=-contentView.getPaddingBottom();
	}

	@Override
	public boolean wantsLightStatusBar(){
		return true;
	}

	@Override
	public boolean wantsLightNavigationBar(){
		return true;
	}

	@Override
	protected void onShown(){
		super.onShown();
		motionEffect.activate();
	}

	@Override
	protected void onHidden(){
		super.onHidden();
		motionEffect.deactivate();
	}

	private void onNext(){
		Instance instance=instancesCache.get("nuwasocial.com");
		if(instance!=null){
			proceedWithAuthOrSignup(instance);
		}else{
			showProgressDialog();
			if(!"nuwasocial.com".equals(loadingInstanceDomain)){
				loadInstanceInfo();
			}
		}
	}
	private void showProgressDialog(){
		instanceProgressDialog=new ProgressDialog(getActivity());
		instanceProgressDialog.setMessage(getString(R.string.loading_instance));
		instanceProgressDialog.setOnCancelListener(dialog->{
			if(loadingInstanceRequest!=null){
				loadingInstanceRequest.cancel();
				loadingInstanceRequest=null;
				loadingInstanceDomain=null;
			}
		});
		instanceProgressDialog.show();
	}
	private void loadInstanceInfo(){
		String domain = "nuwasocial.com";
		Instance cachedInstance=instancesCache.get(domain);
		if(cachedInstance!=null){
			for(CatalogInstance ci:filteredData){
				if(ci.domain.equals(domain))
					return;
			}
			CatalogInstance ci=cachedInstance.toCatalogInstance();
			filteredData.add(0, ci);
			adapter.notifyItemInserted(0);
			return;
		}
		if(loadingInstanceDomain!=null){
			if(loadingInstanceDomain.equals(domain))
				return;
			else
				loadingInstanceRequest.cancel();
		}
		loadingInstanceDomain=domain;
		loadingInstanceRequest=new GetInstance();
		loadingInstanceRequest.setCallback(new Callback<>(){
			@Override
			public void onSuccess(Instance result){
				loadingInstanceRequest=null;
				loadingInstanceDomain=null;
				result.uri=domain; // needed for instances that use domain redirection
				instancesCache.put(domain, result);
				if(instanceProgressDialog!=null){
					instanceProgressDialog.dismiss();
					instanceProgressDialog=null;
					proceedWithAuthOrSignup(result);
				}
				if(Objects.equals(domain, currentSearchQuery)){
					boolean found=false;
					for(CatalogInstance ci:filteredData){
						if(ci.domain.equals(domain)){
							found=true;
							break;
						}
					}
					if(!found){
						CatalogInstance ci=result.toCatalogInstance();
						filteredData.add(0, ci);
						adapter.notifyItemInserted(0);
					}
				}
			}

			@Override
			public void onError(ErrorResponse error){
				loadingInstanceRequest=null;
				loadingInstanceDomain=null;
				if(instanceProgressDialog!=null){
					instanceProgressDialog.dismiss();
					instanceProgressDialog=null;
					new M3AlertDialogBuilder(getActivity())
							.setTitle(R.string.error)
							.setMessage(getString(R.string.not_a_mastodon_instance, domain)+"\n\n"+((MastodonErrorResponse)error).error)
							.setPositiveButton(R.string.ok, null)
							.show();
				}
			}
		}).execNoAuth(domain);
	}
	private void proceedWithAuthOrSignup(Instance instance){
		getActivity().getSystemService(InputMethodManager.class).hideSoftInputFromWindow(contentView.getWindowToken(), 0);
		if(isSignup){
			Bundle args=new Bundle();
			args.putParcelable("instance", Parcels.wrap(instance));
			Nav.go(getActivity(), InstanceRulesFragment.class, args);
		}else{
			AccountSessionManager.getInstance().authenticate(getActivity(), instance);
		}
	}

	private class InstancesAdapter extends UsableRecyclerView.Adapter<InstanceViewHolder>{
		public InstancesAdapter(){
			super(imgLoader);
		}


		@Override
		public InstanceViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
			return null;
		}


		@Override
		public int getItemCount(){
			return filteredData.size();
		}

		@Override
		public int getItemViewType(int position){
			return -1;
		}
	}
	private static class InstanceViewHolder extends BindableViewHolder<CatalogInstance> implements UsableRecyclerView.Clickable{

		public InstanceViewHolder(View itemView) {
			super(itemView);
		}

		@Override
		public void onBind(CatalogInstance item) {

		}

		@Override
		public void onClick() {

		}
	}

}
