import * as THREE from 'three';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader.js';

const MODEL_URL = 'public/assets/body.glb';

const scene = new THREE.Scene();
const camera = new THREE.PerspectiveCamera();
const renderer = new THREE.WebGLRenderer();

const loader = new GLTFLoader();
try {
  const dracoLoader = new DRACOLoader();
  dracoLoader.setDecoderPath('https://www.gstatic.com/draco/versioned/decoders/1.5.6/');
  loader.setDRACOLoader(dracoLoader);
} catch (e) {
  console.log('DRACOLoader not available');
}

loader.load(MODEL_URL, (gltf) => {
  const model = gltf.scene;
  const meshNames: string[] = [];
  model.traverse((child) => {
    if (child.isMesh) {
      meshNames.push(child.name);
    }
  });
  console.log('=== MODEL MESH NAMES ===');
  meshNames.sort().forEach(name => console.log(name));
  console.log('=== TOTAL:', meshNames.length, '===');
  process.exit(0);
}, undefined, (error) => {
  console.error('Load error:', error);
  process.exit(1);
});
