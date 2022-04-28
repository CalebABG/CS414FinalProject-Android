package com.example.cs414finalprojectandroid.retrofit;

import okhttp3.ResponseBody;
import retrofit2.Call;
import retrofit2.http.GET;
import retrofit2.http.Path;

public interface LoremPicsumService {
    @GET("id/182/{w}/{h}")
    Call<ResponseBody> fetchMainScreenImage(@Path("w") String width,
                                          @Path("h") String height);
}
