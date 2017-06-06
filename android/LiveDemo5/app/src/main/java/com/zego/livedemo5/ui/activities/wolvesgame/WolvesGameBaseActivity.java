package com.zego.livedemo5.ui.activities.wolvesgame;

import android.content.Context;
import android.content.Intent;
import android.graphics.Rect;
import android.os.Bundle;
import android.support.v7.widget.GridLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.Toolbar;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.TextureView;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import com.zego.livedemo5.R;
import com.zego.livedemo5.ZegoApiManager;
import com.zego.livedemo5.ui.activities.LogListActivity;
import com.zego.livedemo5.ui.activities.base.AbsBaseLiveActivity;
import com.zego.livedemo5.utils.PreferenceUtil;
import com.zego.zegoliveroom.ZegoLiveRoom;
import com.zego.zegoliveroom.constants.ZegoVideoViewMode;

import org.json.JSONException;
import org.json.JSONObject;

import java.util.LinkedList;
import java.util.Timer;

import butterknife.Bind;

public abstract class WolvesGameBaseActivity extends AbsBaseLiveActivity {

    static final protected String kSpeakingCommandKey = "command";

    static final protected String kSpeakingUserIdKey  = "userId";

    static final protected String kCurrentUserListKey = "currentUserList";
    static final protected String kNewUserKey         = "newUser";

    static final protected String kUserIdKey          = "userId";
    static final protected String kUserNameKey        = "userName";

    static final protected String kSpeakingModeKey    = "roomMode";
    static final protected String kUserIndexKey       = "userIndex";
    static final protected String kServerModeIndexKey = "urtralServer";

    static final protected String kUserCharacterKey   = "character";

    /** 发信停止说话信令与停止推流之间的时间间隔，用于确保按停止说话按钮时，所有语音都能正常推送出去 */
    static final protected int kPostSpeakingInterval  = 2;
    /** 开始说话后，设置结束说话倒计时。即轮流模式下允许单次说话的最长时间 */
    static final protected int kSpeakingTimerInterval = 60;
    /** 轮流说话模式下，组织者（Host）用于设定某位说话者的最长时间，避免说话者异常离开导致游戏无法继续的情况 */
    static final protected int kAnchorTimerInterval   = 5 + kSpeakingTimerInterval;

    @Bind(R.id.toolbar)
    public Toolbar toolBar;

    @Bind(R.id.small_view_container)
    public RecyclerView mJoinedUsers;

    @Bind(R.id.vl_big_view)
    public TextureView mCurrentSpeakingHead;

    @Bind(R.id.txt_current_status)
    public TextView mTextTips;


    @Bind(R.id.wolf_role)
    public TextView mTextRole;

    @Bind(R.id.wolf_timer_count)
    public TextView mTimerView;

    @Bind(R.id.btn_start_or_stop_speaking)
    public Button mBtnSpeaking;

    @Bind(R.id.in_turn_speaking)
    public TextView mInTurnSpeaking;

    @Bind(R.id.end_in_turn_speaking)
    public TextView mEndInTurnSpeaking;

    protected RecyclerGridViewAdapter mRecyclerAdapter;

    /** 进入房间的所有成员列表 */
    protected LinkedList<WolfInfo> allWolfMembers;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.menu_wolves_game, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.log: {
                Intent intent = new Intent(this, LogListActivity.class);
                startActivity(intent);
            }
                return true;

            default:
                return super.onOptionsItemSelected(item);
        }
    }

    /**
     * 获取内容页面的布局.
     *
     * @return 返回内容页面的布局
     */
    @Override
    protected int getContentViewLayout() {
        return R.layout.activity_wolves_game;
    }


    /**
     * 初始化子类中的变量.
     */
    @Override
    protected void initVariables(Bundle savedInstanceState) {
        allWolfMembers = new LinkedList<>();
        mRecyclerAdapter = new RecyclerGridViewAdapter(this);
    }

    /**
     * 初始化子类中的控件.
     */
    @Override
    protected void initViews(Bundle savedInstanceState) {
        int spanCount = 4;
        mJoinedUsers.addItemDecoration(new GridSpacingItemDecoration(spanCount, 10, false));
        mJoinedUsers.setAdapter(mRecyclerAdapter);

        GridLayoutManager mgr = new GridLayoutManager(this, spanCount);
        mJoinedUsers.setLayoutManager(mgr);

        setSupportActionBar(toolBar);
        toolBar.setNavigationOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                onBackPressed();
            }
        });

        mBtnSpeaking.setEnabled(false);
        mInTurnSpeaking.setEnabled(false);
        mEndInTurnSpeaking.setEnabled(false);

        mTextRole.setVisibility(View.INVISIBLE);
    }

    protected JSONObject encodeWolfInfo(WolfInfo wolf) throws JSONException {
        JSONObject json = new JSONObject();
        json.put(kUserIdKey, wolf.getUserId());
        json.put(kUserNameKey, wolf.getUserName());
        json.put(kUserIndexKey, wolf.getIndex());
        return json;
    }

    protected boolean isMe(String userId) {
        return (TextUtils.isEmpty(userId) || userId.equals(PreferenceUtil.getInstance().getUserID()));
    }

    protected static class RecyclerGridViewAdapter extends RecyclerView.Adapter<WolfHeadViewHolder> {
        private LayoutInflater layoutInflater;

        private LinkedList<WolfInfo> mData;
        private ZegoLiveRoom zegoLiveRoom;

        private int currentSpeakingMode = SpeakingMode.FreeSpeakingMode;

        RecyclerGridViewAdapter(Context context) {
            layoutInflater = LayoutInflater.from(context);
            mData = new LinkedList<>();
            zegoLiveRoom = ZegoApiManager.getInstance().getZegoLiveRoom();
        }

        @Override
        public WolfHeadViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View view = layoutInflater.inflate(R.layout.view_wolf_item, parent, false);
            return new WolfHeadViewHolder(view);
        }

        @Override
        public void onBindViewHolder(WolfHeadViewHolder holder, int position) {
            if (holder == null) return;

            WolfInfo wolf = mData.get(position);
            holder.serialNumber.setText(String.valueOf(wolf.getIndex()));
            holder.name.setText(wolf.getUserName());
            holder.streamId = wolf.getStreamId();
            if (!TextUtils.isEmpty(holder.streamId)) {
                if (currentSpeakingMode == SpeakingMode.FreeSpeakingMode) {
                    String selfUserId = PreferenceUtil.getInstance().getUserID();
                    if (TextUtils.equals(selfUserId, wolf.getUserId())) {
                        zegoLiveRoom.setPreviewView(holder.headImg);
                        zegoLiveRoom.setPreviewViewMode(ZegoVideoViewMode.ScaleAspectFill);
                        zegoLiveRoom.startPreview();
                    } else {
                        zegoLiveRoom.setViewMode(ZegoVideoViewMode.ScaleAspectFill, holder.streamId);
                        zegoLiveRoom.startPlayingStream(holder.streamId, holder.headImg);
                    }
                    holder.headImg.setVisibility(View.VISIBLE);
                } else {
                    holder.headImg.setVisibility(View.INVISIBLE);
                    holder.itemView.setBackgroundResource(R.color.bg_gray_deep);
                }
            } else {
                holder.headImg.setVisibility(View.INVISIBLE);
                holder.itemView.setBackgroundResource(R.color.bg_gray);
            }
        }

        @Override
        public void onViewRecycled(WolfHeadViewHolder holder) {
            if (!TextUtils.isEmpty(holder.streamId)) {
                zegoLiveRoom.stopPlayingStream(holder.streamId);
                holder.headImg.setVisibility(View.INVISIBLE);
            }
        }

        @Override
        public int getItemCount() {
            return mData.size();
        }

        public void setCurrentSpeakingMode(int mode) {
            currentSpeakingMode = mode;
        }

        public void updateData(LinkedList<WolfInfo> data, boolean notify) {
            mData.clear();
            mData.addAll(data);
            if (notify) {
                notifyDataSetChanged();
            }
        }

        public void updateItem(WolfInfo wolf) {
            int pos = -1;
            for (WolfInfo _wolf : mData) {
                pos ++;
                if (_wolf.equals(wolf)) {
                    _wolf.update(wolf);
                    notifyItemChanged(pos);
                    break;
                }
            }
        }

        public void insertItem(WolfInfo wolf) {
            if (wolf != null) {
                mData.add(wolf);
                notifyItemInserted(mData.size() - 1);
            }
        }

        public void deleteItem(WolfInfo wolf) {
            int pos = -1;
            for (WolfInfo _wolf : mData) {
                pos ++;
                if (_wolf.equals(wolf)) {
                    mData.remove(_wolf);
                    notifyItemRemoved(pos);
                    break;
                }
            }
        }
    }

    protected static class WolfHeadViewHolder extends RecyclerView.ViewHolder {
        TextureView headImg;
        TextView serialNumber;
        TextView name;

        String streamId;

        public WolfHeadViewHolder(View itemView) {
            super(itemView);

            headImg = (TextureView)itemView.findViewById(R.id.head_img);
            serialNumber = (TextView)itemView.findViewById(R.id.txt_serial_number);
            name = (TextView)itemView.findViewById(R.id.txt_name);
        }
    }

    /**
     * RecyclerView Item间距
     */
    protected static class GridSpacingItemDecoration extends RecyclerView.ItemDecoration {

        private int spanCount;
        private int spacing;
        private boolean includeEdge;

        /**
         *
         * @param spanCount 每行多少个元素
         * @param spacing 元素间间距
         * @param includeEdge 边缘地带是否需要间距。false 无间距
         */
        public GridSpacingItemDecoration(int spanCount, int spacing, boolean includeEdge) {
            this.spanCount = spanCount;
            this.spacing = spacing;
            this.includeEdge = includeEdge;
        }

        @Override
        public void getItemOffsets(Rect outRect, View view, RecyclerView parent, RecyclerView.State state) {
            int position = parent.getChildAdapterPosition(view); // item position
            int column = position % spanCount; // item column

            if (includeEdge) {
                outRect.left = spacing - column * spacing / spanCount; // spacing - column * ((1f / spanCount) * spacing)
                outRect.right = (column + 1) * spacing / spanCount; // (column + 1) * ((1f / spanCount) * spacing)

                if (position < spanCount) { // top edge
                    outRect.top = spacing;
                }
                outRect.bottom = spacing; // item bottom
            } else {
                outRect.left = column * spacing / spanCount; // column * ((1f / spanCount) * spacing)
                outRect.right = spacing - (column + 1) * spacing / spanCount; // spacing - (column + 1) * ((1f /    spanCount) * spacing)
                if (position >= spanCount) {
                    outRect.top = spacing; // item top
                }
            }
        }
    }
}
