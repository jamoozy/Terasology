/*
 * Copyright 2012 Benjamin Glatzel <benjamin.glatzel@me.com>
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
package org.terasology.rendering.primitives;

import static org.lwjgl.opengl.GL11.GL_COLOR_ARRAY;
import static org.lwjgl.opengl.GL11.GL_CULL_FACE;
import static org.lwjgl.opengl.GL11.GL_NORMAL_ARRAY;
import static org.lwjgl.opengl.GL11.GL_TEXTURE_COORD_ARRAY;
import static org.lwjgl.opengl.GL11.GL_VERTEX_ARRAY;
import static org.lwjgl.opengl.GL11.glColorPointer;
import static org.lwjgl.opengl.GL11.glDisable;
import static org.lwjgl.opengl.GL11.glDisableClientState;
import static org.lwjgl.opengl.GL11.glEnable;
import static org.lwjgl.opengl.GL11.glEnableClientState;
import static org.lwjgl.opengl.GL11.glNormalPointer;
import static org.lwjgl.opengl.GL11.glTexCoordPointer;
import static org.lwjgl.opengl.GL11.glVertexPointer;
import gnu.trove.list.TFloatList;
import gnu.trove.list.TIntList;
import gnu.trove.list.array.TFloatArrayList;
import gnu.trove.list.array.TIntArrayList;

import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.util.concurrent.locks.ReentrantLock;

import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL13;
import org.lwjgl.opengl.GL15;
import org.terasology.logic.manager.VertexBufferObjectManager;

import com.bulletphysics.collision.shapes.IndexedMesh;

/**
 * Chunk meshes are used to store the vertex data of tessellated chunks.
 */
public class ChunkMesh {

    /**
     * Data structure for storing vertex data. Abused like a "struct" in C/C++. Just sad.
     */
    public static class VertexElements {

        public VertexElements() {
            vtxCount = 0;

            normals = new TFloatArrayList();
            vertices = new TFloatArrayList();
            tex = new TFloatArrayList();
            color = new TFloatArrayList();
            indices = new TIntArrayList();
            flags = new TIntArrayList();
        }

        public final TFloatList normals;
        public final TFloatList vertices;
        public final TFloatList tex;
        public final TFloatList color;
        public final TIntList indices;
        public final TIntList flags;
        public int vtxCount;

        public ByteBuffer finalVertices;
        public IntBuffer finalIndices;
    }

    /**
     * Possible rendering types.
     */
    public enum RENDER_TYPE {
        OPAQUE(0),
        TRANSLUCENT(1),
        BILLBOARD(2),
        WATER_AND_ICE(3);

        private int _meshIndex;

        private RENDER_TYPE(int index) {
            _meshIndex = index;
        }

        public int getIndex() {
            return _meshIndex;
        }
    }

    public enum RENDER_PHASE {
        OPAQUE,
        ALPHA_REJECT,
        REFRACTIVE,
        Z_PRE_PASS
    }

    /* CONST */
    public static final int SIZE_VERTEX = 3;
    public static final int SIZE_TEX0 = 3;
    public static final int SIZE_TEX1 = 3;
    public static final int SIZE_COLOR = 1;
    public static final int SIZE_NORMAL = 3;

    public static final int OFFSET_VERTEX = 0;
    public static final int OFFSET_TEX_0 = OFFSET_VERTEX + SIZE_VERTEX * 4;
    public static final int OFFSET_TEX_1 = OFFSET_TEX_0 + SIZE_TEX0 * 4;
    public static final int OFFSET_COLOR = OFFSET_TEX_1 + SIZE_TEX1 * 4;
    public static final int OFFSET_NORMAL = OFFSET_COLOR + SIZE_COLOR * 4;
    public static final int STRIDE = OFFSET_NORMAL + SIZE_NORMAL * 4;

    /* VERTEX DATA */
    private final int[] vertexBuffers = new int[4];
    private final int[] idxBuffers = new int[4];
    private final int[] vertexCount = new int[4];

    /* STATS */
    private int triangleCount = -1;

    /* TEMPORARY DATA */
    public VertexElements[] vertexElements = new VertexElements[4];

    /* BULLET PHYSICS */
    public IndexedMesh indexedMesh = null;
    private boolean disposed = false;

    /* CONCURRENCY */
    public ReentrantLock lock = new ReentrantLock();
    
    /* MEASUREMENTS */
    public int timeToGenerateBlockVertices = 0;
    public int timeToGenerateOptimizedBuffers = 0;
    
    public ChunkMesh() {
        vertexElements[0] = new VertexElements();
        vertexElements[1] = new VertexElements();
        vertexElements[2] = new VertexElements();
        vertexElements[3] = new VertexElements();
    }

    /**
     * Generates the VBOs from the pre calculated arrays.
     *
     * @return True if something was generated
     */
    public boolean generateVBOs() {
        if (lock.tryLock()) {
            try {
                // IMPORTANT: A mesh can only be generated once.
                if (vertexElements == null || disposed)
                    return false;

                for (int i = 0; i < vertexBuffers.length; i++)
                    generateVBO(i);

                // Free unused space on the heap
                vertexElements = null;
                // Calculate the final amount of triangles
                triangleCount = (vertexCount[0] + vertexCount[1] + vertexCount[2] + vertexCount[3]) / 3;
            } finally {
                lock.unlock();
            }

            return true;
        }

        return false;
    }

    private void generateVBO(int id) {
        if (lock.tryLock()) {
            try {
                if (!disposed && vertexElements[id].finalIndices.limit() > 0 && vertexElements[id].finalVertices.limit() > 0) {
                    vertexBuffers[id] = VertexBufferObjectManager.getInstance().getVboId();
                    idxBuffers[id] = VertexBufferObjectManager.getInstance().getVboId();
                    vertexCount[id] = vertexElements[id].finalIndices.limit();

                    VertexBufferObjectManager.getInstance().bufferVboElementData(idxBuffers[id], vertexElements[id].finalIndices, GL15.GL_STATIC_DRAW);
                    VertexBufferObjectManager.getInstance().bufferVboData(vertexBuffers[id], vertexElements[id].finalVertices, GL15.GL_STATIC_DRAW);
                } else {
                    vertexBuffers[id] = 0;
                    idxBuffers[id] = 0;
                    vertexCount[id] = 0;
                }
            } finally {
                lock.unlock();
            }
        }
    }

    private void renderVbo(int id) {
        if (lock.tryLock()) {
            try {
                if (vertexBuffers[id] <= 0 || disposed)
                    return;

                glEnableClientState(GL_VERTEX_ARRAY);
                glEnableClientState(GL_TEXTURE_COORD_ARRAY);
                glEnableClientState(GL_COLOR_ARRAY);
                glEnableClientState(GL_NORMAL_ARRAY);

                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, idxBuffers[id]);
                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, vertexBuffers[id]);

                glVertexPointer(SIZE_VERTEX, GL11.GL_FLOAT, STRIDE, OFFSET_VERTEX);

                GL13.glClientActiveTexture(GL13.GL_TEXTURE0);
                glTexCoordPointer(SIZE_TEX0, GL11.GL_FLOAT, STRIDE, OFFSET_TEX_0);

                GL13.glClientActiveTexture(GL13.GL_TEXTURE1);
                glTexCoordPointer(SIZE_TEX1, GL11.GL_FLOAT, STRIDE, OFFSET_TEX_1);

                glColorPointer(SIZE_COLOR*4, GL11.GL_UNSIGNED_BYTE, STRIDE, OFFSET_COLOR);

                glNormalPointer(GL11.GL_FLOAT, STRIDE, OFFSET_NORMAL);

                GL11.glDrawElements(GL11.GL_TRIANGLES, vertexCount[id], GL11.GL_UNSIGNED_INT, 0);

                glDisableClientState(GL_NORMAL_ARRAY);
                glDisableClientState(GL_COLOR_ARRAY);
                glDisableClientState(GL_TEXTURE_COORD_ARRAY);
                glDisableClientState(GL_VERTEX_ARRAY);

                GL15.glBindBuffer(GL15.GL_ARRAY_BUFFER, 0);
                GL15.glBindBuffer(GL15.GL_ELEMENT_ARRAY_BUFFER, 0);
            } finally {
                lock.unlock();
            }
        }
    }

    public void render(RENDER_PHASE type) {
        switch (type) {
            case OPAQUE:
                renderVbo(0);
                break;
            case ALPHA_REJECT:
                renderVbo(1);
                glDisable(GL_CULL_FACE);
                renderVbo(2);
                glEnable(GL_CULL_FACE);
                break;
            case REFRACTIVE:
                renderVbo(3);
                break;
        }
    }

    public void dispose() {
        lock.lock();

        try {
            if (!disposed) {
                for (int i = 0; i < vertexBuffers.length; i++) {
                    int id = vertexBuffers[i];

                    VertexBufferObjectManager.getInstance().putVboId(id);
                    vertexBuffers[i] = 0;

                    id = idxBuffers[i];

                    VertexBufferObjectManager.getInstance().putVboId(id);
                    idxBuffers[i] = 0;
                }

                disposed = true;
                vertexElements = null;
                indexedMesh = null;
            }
        } finally {
            lock.unlock();
        }
    }

    public boolean isDisposed() {
        return disposed;
    }

    public int triangleCount(RENDER_PHASE phase) {
        if (phase == RENDER_PHASE.OPAQUE)
            return vertexCount[0] / 3;
        else if (phase == RENDER_PHASE.ALPHA_REJECT)
            return (vertexCount[1] + vertexCount[2]) / 3;
        else
            return vertexCount[3] / 3;
    }

    public int triangleCount() {
        return triangleCount;
    }

    public boolean isEmpty() {
        return triangleCount == 0;
    }
}
