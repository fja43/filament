/*
 * Copyright (C) 2020 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.android.filament.pagecurl;

import com.google.android.filament.Engine;
import com.google.android.filament.Entity;
import com.google.android.filament.IndexBuffer;
import com.google.android.filament.MaterialInstance;
import com.google.android.filament.MathUtils;
import com.google.android.filament.VertexBuffer;

import java.nio.FloatBuffer;

@SuppressWarnings("WeakerAccess")
public class Page {
    public @Entity
    int renderable;
    public MaterialInstance material;
    public VertexBuffer vertexBuffer;
    public IndexBuffer indexBuffer;

    // TODO: remove these in favor of GPU animation
    public FloatBuffer uvs;
    public FloatBuffer positions;
    public FloatBuffer normals;
    public FloatBuffer tangents;

    // Takes an animation value in [0,1] and updates the positions array.
    // Returns a rotation value that should be applied to the entire page.
    //
    // NOTE: This routine comes up with reasonable values for two artist-controlled time-varying
    // parameters: theta and apex. Feel free to modify according to taste.
    public float updatePositions(Engine engine, float t) {
        t = Math.min(1.0f, Math.max(0.0f, t));
        final float D0 = 0.15f;
        final float D1 = 1.0f - D0;
        double deformation = D0 + (1.0 + Math.cos(8.0 * Math.PI * t)) * D1 / 2.0;
        if (t > 0.125) {
            deformation = D0;
        }
        if (t > 0.5) {
            final double t1 = Math.max(0.0, (t - 0.5) / 0.5 - 0.125);
            deformation = D0 + D1 * Math.pow(t1, 3.0);
        }
        final double apex = -15 * deformation;
        final double theta = deformation * Math.PI / 2.0;
        this.deformMesh((float)theta, (float)apex);

        vertexBuffer.setBufferAt(engine, 0, positions);
        vertexBuffer.setBufferAt(engine, 2, tangents);

        final double radians = Math.PI * Math.max(0.0, (t - 0.125) / 0.875);
        return (float) radians;
    }

    private void deformMesh(float theta, float apex) {
        final float ty = -0.5f;
        final float e = 0.01f;
        final int count = this.positions.capacity() / 3;

        float[] p = new float[3];
        float[] p1 = new float[3];
        float[] p2 = new float[3];
        float[] du = new float[3];
        float[] dv = new float[3];
        float[] n = new float[3];
        float[] q = new float[4];

        for (int i = 0; i < count; i++) {
            final float u = this.uvs.get(i * 2);
            final float v = this.uvs.get(i * 2 + 1);

            deformPoint(theta, apex, u, v, p);
            deformPoint(theta, apex, u + e, v, p1);
            deformPoint(theta, apex, u, v + e, p2);

            subtractVectors(du, p1, p);
            subtractVectors(dv, p2, p);
            normalize(du);
            normalize(dv);
            crossProduct(n, du, dv);
            normalize(n);

            MathUtils.packTangentFrame(
                    du[0], du[1], du[2],
                    dv[0], dv[1], dv[2],
                    n[0], n[1], n[2],
                    q);

            this.tangents.put(i * 4, q[0]);
            this.tangents.put(i * 4 + 1, q[1]);
            this.tangents.put(i * 4 + 2, q[2]);
            this.tangents.put(i * 4 + 3, q[3]);

            this.positions.put(i * 3, p[0]);
            this.positions.put(i * 3 + 1, p[1] + ty);
            this.positions.put(i * 3 + 2, p[2]);
        }
    }

    // Applies the deformation described in "Deforming Pages of Electronic Books" by Hong et al.
    //
    // NOTE: If the animation needs to be tweaked, this routine should probably be left untouched;
    // instead, the calling code should tweak how it produces values for "theta" and "apex".
    private void deformPoint(float theta, float apex, float u, float v, float[] outPosition) {
        final double r = Math.sqrt(u * u + (v - apex) * (v - apex));
        final double d = r * Math.sin(theta);
        final double alpha = Math.asin(u / r);
        final double beta = alpha / Math.sin(theta);

        final double x = d * Math.sin(beta);
        final double y = r + apex - d * (1 - Math.cos(beta)) * Math.sin(theta);
        final double z = d * (1 - Math.cos(beta)) * Math.cos(theta);

        outPosition[0] = (float) x;
        outPosition[1] = (float) y;
        outPosition[2] = (float) z;
    }

    private void subtractVectors(float[] out, float[] a, float[] b) {
        out[0] = a[0] - b[0];
        out[1] = a[1] - b[1];
        out[2] = a[2] - b[2];
    }

    private void crossProduct(float[] out, float[] a, float[] b) {
        out[0] = a[1] * b[2] - a[2] * b[1];
        out[1] = a[2] * b[0] - a[0] * b[2];
        out[2] = a[0] * b[1] - a[1] * b[0];
    }

    private void normalize(float[] inout) {
        final double d = inout[0] * inout[0] + inout[1] * inout[1] + inout[2] * inout[2];
        final double inv = 1.0 / Math.sqrt(d);
        inout[0] *= inv;
        inout[1] *= inv;
        inout[2] *= inv;
    }
}