package com.coolweather.android;

import android.app.Fragment;
import android.app.ProgressDialog;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.coolweather.android.db.City;
import com.coolweather.android.db.County;
import com.coolweather.android.db.Province;
import com.coolweather.android.util.HttpUtil;
import com.coolweather.android.util.Utility;

import org.litepal.crud.DataSupport;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.Response;

/**
 * Created by CoderToday on 2018/2/28.
 */

public class ChooseAreaFragment extends Fragment {
    private static final int LEVEL_PROVINCE = 0;
    private static final int LEVEL_CITY = 1;
    private static final int LEVEL_COUNTY = 2;
    private TextView titleText;
    private Button backButton;
    private ListView listView;
    private ArrayAdapter<String> adapter;
    private ProgressDialog progressDialog;
    private ArrayList<String> dataList = new ArrayList<>();
    /**
     * 当前选中的级别  是省？市？县？
     */
    private int currentLevel;
    /**
     * 选中的省份
     */
    private Province selectedProvince;
    /**
     * 所有省的集合
     */
    private List<Province> provinceList;
    /**
     * 选中的城市
     */
    private City selectedCity;
    /**
     * 某个省下的所有市的集合
     */
    private List<City> cityList;
    /**
     * 某个市下所有县的集合
     */
    private List<County> countyList;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.choose_area, container, false);
        titleText = (TextView) view.findViewById(R.id.title_text);
        backButton = (Button) view.findViewById(R.id.back_button);
        listView = (ListView) view.findViewById(R.id.list_view);
        adapter = new ArrayAdapter<>(getContext(), android.R.layout.simple_list_item_1, dataList);
        listView.setAdapter(adapter);
        return view;
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                //省市县的数据都有可能显示在列表中，点击省市县之后的操作是不一样的，所以需要判断当前的列表显示的是哪一个
                if (currentLevel == LEVEL_PROVINCE) {//点击省，查询市
                    selectedProvince = provinceList.get(position);
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {//点击市，查询县
                    selectedCity = cityList.get(position);
                    queryCounties();
                }
            }
        });
        backButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (currentLevel == LEVEL_COUNTY) {//当前县，返回市
                    queryCities();
                } else if (currentLevel == LEVEL_CITY) {//当前是市，返回省
                    queryProvinces();
                }
            }
        });
        queryProvinces();//第一次进先查询所有省
    }

    /**
     * 查询全国所有的省，先从数据库查，如果数据库没有再从网络获取
     */
    private void queryProvinces() {
        titleText.setText("中国");
        backButton.setVisibility(View.GONE);
        provinceList = DataSupport.findAll(Province.class);
        if (provinceList.size() > 0) {//从数据库查询到数据了
            dataList.clear();
            for (Province province : provinceList) {//把每个省份的名字取出来放到一个集合中
                dataList.add(province.getProvinceName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_PROVINCE;
        } else {//数据库没有数据，去网络上查询
            String address = "http://guolin.tech/api/china";
            queryFromServer(address, "province");
        }
    }

    /**
     * 查询选中的省内的所有的市，先从数据库查，如果数据库没有就从网络获取
     */
    private void queryCities() {
        titleText.setText(selectedProvince.getProvinceName());//从选中的省对象中取出名字
        backButton.setVisibility(View.VISIBLE);
        //注意搜索条件
        cityList = DataSupport.where("provinceid=?", String.valueOf(selectedProvince.getId())).find(City.class);
        if (cityList.size() > 0) {
            dataList.clear();
            for (City city : cityList) {
                dataList.add(city.getCityName());
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_CITY;
        } else {//根据选中省份的code,去查询该省份下所有的城市
            String address = "http://guolin.tech/api/china/" + selectedProvince.getProvinceCode();
            queryFromServer(address, "city");
        }
    }

    /**
     * 查询选中的市内的所有的县，先从数据库查，数据库查不到再从网络上获取
     */
    private void queryCounties() {
        titleText.setText(selectedCity.getCityName());
        backButton.setVisibility(View.VISIBLE);
        countyList = DataSupport.where("cityid = ?", String.valueOf(selectedCity.getId())).find(County.class);
        if (countyList.size() > 0) {
            dataList.clear();
            for (County county : countyList) {
                dataList.add(county.getCountyName());//获取某个市下所有县的名字
            }
            adapter.notifyDataSetChanged();
            listView.setSelection(0);
            currentLevel = LEVEL_COUNTY;
        } else {
            String address = "http://guolin.tech/api/china/" + selectedProvince.getProvinceCode() + "/" + selectedCity.getCityCode();
            queryFromServer(address, "county");
        }
    }

    /**
     * 根据传入的地址和类型从服务器上查询省市县数据
     *
     * @param address
     */
    private void queryFromServer(String address, final String type) {
        showProgressDialog();
        HttpUtil.sendOkHttpRequest(address, new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                getActivity().runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        closeProgressDialog();
                        Toast.makeText(getContext(),"加载失败",Toast.LENGTH_SHORT).show();
                    }
                });
            }

            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String responseText = response.body().string();
                boolean result = false;
                //把服务器返回的数据解析一下保存到数据库
                if ("province".equals(type)) {
                    result = Utility.handleProvinceResponse(responseText);
                } else if ("city".equals(type)) {
                    result = Utility.handleCityResponse(responseText, selectedProvince.getId());
                } else if ("county".equals(type)) {
                    result = Utility.handleCountyResponse(responseText, selectedCity.getId());
                }

                //如果从网络获取的数据成功地保存到了数据库，则将数据展示出来(注意此处是在子线程)
                if(result){
                    getActivity().runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            //从网络获取数据成功之后先把加载框给关了
                            closeProgressDialog();
                            //从网络获取到数据之后，先保存，再从数据库把数据取出，再展示（不是从网络获取数据之后直接展示，要为以后着想）
                            if("county".equals(type)){
                                queryCounties();
                            }else if("city".equals(type)){
                                queryCities();
                            }else if("province".equals(type)){
                                queryProvinces();
                            }
                        }
                    });
                }
            }
        });
    }

    /**
     * 关闭进度对话框
     */
    private void closeProgressDialog() {
        if(progressDialog!=null&&progressDialog.isShowing()){
            progressDialog.dismiss();
        }
    }


    /**
     * 显示加载进度框
     */
    private void showProgressDialog() {
        if(progressDialog==null){
            progressDialog = new ProgressDialog(getActivity());
            progressDialog.setMessage("正在加载...");
            progressDialog.setCanceledOnTouchOutside(false);
        }
        progressDialog.show();
    }
}
