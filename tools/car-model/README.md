# Car model pipeline

`app/src/main/assets/models/shine.glb` is generated from the Sketchfab model
["Aeolus Yixuan" by teenlin3](https://sketchfab.com/3d-models/aeolus-yixuan-3b164088d4114ae787ff5c1504d75837)
(CC BY 4.0) with `split.mjs`:

```sh
npm install @gltf-transform/core@4.1.1 @gltf-transform/extensions@4.1.1 \
  @gltf-transform/functions@4.1.1 meshoptimizer sharp
node --max-old-space-size=6000 split.mjs aeolus_yixuan.glb shine.glb 0.12
```

It bakes the transforms, cuts the mesh into nodes the app animates
(`door_fl/fr/rl/rr`, `trunk`, `hood`, `steer_xx` > `wheel_xx` + `caliper_xx`),
each pivoted on its real hinge/axle, simplifies with meshoptimizer and
shrinks the 4K PNG textures to 2K/1K JPEG. The root node `car` is scaled
to metres (4.68 m long). +z = front, +x = the car's left side.
