/*
 * Copyright 2018. Gatekeeper Contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.finra.gatekeeper.services.aws.model;

import com.google.common.base.MoreObjects;

import java.util.Objects;

/**
 * Simple POJO used to manage cache.
 */
public class AWSEnvironment {
    private String account;
    private String region;

    public AWSEnvironment(String account, String region){
        this.account = account;
        this.region = region;
    }

    public String getAccount(){
        return account;
    }

    public String getRegion(){
        return region;
    }

    @Override
    public boolean equals(Object o){
        if(this == o){
            return true;
        }

        if (o == null || !getClass().equals(o.getClass())) {
            return false;
        }

        AWSEnvironment that = (AWSEnvironment)o;
        return Objects.equals(this.account, that.getAccount()) && Objects.equals(this.region, that.getRegion());
    }


    @Override
    public int hashCode() {
        return Objects.hash(account, region);
    }

    @Override
    public String toString() {
        return MoreObjects.toStringHelper(this)
                .add("account", account)
                .add("region", region)
                .toString();
    }
}
