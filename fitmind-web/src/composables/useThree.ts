import { ref, onUnmounted, markRaw } from 'vue';
import * as THREE from 'three';
import { OrbitControls } from 'three/examples/jsm/controls/OrbitControls.js';
import { GLTFLoader } from 'three/examples/jsm/loaders/GLTFLoader.js';
import { DRACOLoader } from 'three/examples/jsm/loaders/DRACOLoader.js';

const HIGHLIGHT_COLOR = 0xff2222;
const EMISSIVE_COLOR = 0x660000;
const HOVER_COLOR = 0xff8800;
const HOVER_EMISSIVE = 0x553300;

type ColorEditableMaterial = THREE.Material & {
  color?: THREE.Color;
  emissive?: THREE.Color;
};

const EXCLUDE_PATTERNS = [
  'bursa', 'tendon sheath', 'subtendinous', 'trochlea',
  'groove for', 'tubercle', 'tuberosity', 'crest of', 'fossa',
  'squamous part', 'tympanic part', 'orbital part', 'palpebral part',
  'cartilage', 'ligament', 'retinaculum', 'fascia', 'septum',
  'joint capsule', 'synovial', 'articulation',
  'part of muscular system', 'compartment of',
  'superficial muscles', 'deep muscles', 'muscles of',
  'anterior compartment', 'posterior compartment',
  'rotator cuff muscles', 'spinotransversales muscles',
  'transversospinal muscles', 'suboccipital muscles',
  'suprahyoid muscles', 'infrahyoid muscles',
  'extraocular muscles', 'muscles of facial expression',
  'muscles of mastication', 'muscles of tongue',
  'muscles of pharynx', 'muscles of larynx',
  'intrinsic muscles', 'extrinsic muscles',
  'superficial part of anterior compartment',
  'superficial part of posterior compartment',
  'superficial part of masseter',
  'superficial gluteal muscles', 'deep gluteal muscles',
  'anterior forearm muscles', 'posterior forearm muscles',
  'thenar muscles', 'hypothenar muscles',
  'lumbrical', 'interosseous',
  'deltoid tubercle', 'deltoid tuberosity',
  'supinator crest',
];

const BLENDER_SUFFIX_REGEX = /\.\d{3}$/;

const DEBUG = true;
function log(...args: unknown[]) {
  if (DEBUG) console.log('[useThree]', ...args);
}

function normalizeMeshName(name: string): string {
  return name.replace(BLENDER_SUFFIX_REGEX, '').toLowerCase().trim();
}

function tokenize(name: string): string[] {
  return name.toLowerCase().replace(BLENDER_SUFFIX_REGEX, '').split(/[\s\-_,.]+/).filter(t => t.length > 2);
}

function isMuscleMesh(lowerName: string): boolean {
  const muscleIndicators = [
    'muscle', 'head of', 'part of', 'biceps', 'triceps', 'deltoid',
    'gluteus', 'pectoralis', 'latissimus', 'trapezius', 'quadriceps',
    'hamstring', 'serratus', 'oblique', 'abdominis', 'erector',
    'soleus', 'gastrocnemius', 'tibialis', 'brachialis', 'rhomboid',
    'scalene', 'splenius', 'spinalis', 'semispinalis', 'iliocostalis',
    'longissimus', 'multifidus', 'rotatores', 'intercostal',
    'piriformis', 'gemellus', 'obturator', 'popliteus', 'plantaris',
    'peroneus', 'fibularis', 'gracilis', 'sartorius', 'tensor',
    'coracobrachialis', 'anconeus', 'supinator', 'pronator',
    'flexor', 'extensor', 'adductor', 'abductor', 'levator',
    'diaphragm', 'psoas', 'quadratus', 'iliacus', 'pectineus',
    'vastus', 'rectus', 'brevis', 'longus', 'major', 'minor',
    'masseter', 'temporalis', 'sternocleidomastoid',
    'digastric', 'mylohyoid', 'geniohyoid', 'stylohyoid',
    'thyrohyoid', 'sternohyoid', 'omohyoid',
    'frontalis', 'occipitalis', 'orbicularis', 'zygomatic',
    'coccygeus', 'cricothyroid', 'arytenoid', 'constrictor',
    'palatoglossus', 'palatopharyngeus', 'stylopharyngeus',
    'hyoglossus', 'genioglossus', 'mentalis', 'risorius',
    'procerus', 'nasalis', 'buccinator',
  ];
  return muscleIndicators.some(kw => lowerName.includes(kw));
}

interface MatchLogEntry {
  timestamp: number;
  keywords: string[];
  matchedKeywords: string[];
  unmatchedKeywords: string[];
  matchDetails: Map<string, string[]>;
}

const KNOWN_MISSING_MESHES = new Set<string>([
]);

function getColorEditableMaterials(mesh: THREE.Mesh): ColorEditableMaterial[] {
  const materials = Array.isArray(mesh.material) ? mesh.material : [mesh.material];
  return materials as ColorEditableMaterial[];
}

function cloneMeshMaterials(mesh: THREE.Mesh) {
  if (Array.isArray(mesh.material)) {
    mesh.material = mesh.material.map((material) => markRaw(material.clone()));
  } else {
    mesh.material = markRaw(mesh.material.clone());
  }
}

export function useThree() {
  const scene = ref<THREE.Scene | null>(null);
  const camera = ref<THREE.PerspectiveCamera | null>(null);
  const renderer = ref<THREE.WebGLRenderer | null>(null);
  const controls = ref<OrbitControls | null>(null);
  const currentModel = ref<THREE.Object3D | null>(null);
  const muscleMeshes = ref<Map<string, THREE.Mesh>>(new Map());
  const modelLoaded = ref(false);
  const loading = ref(false);
  const loadProgress = ref(0);
  const loadError = ref<string | null>(null);
  const hoveredMeshName = ref<string | null>(null);
  const matchLog = ref<MatchLogEntry[]>([]);

  let containerEl: HTMLElement | null = null;
  let resizeObserver: ResizeObserver | null = null;
  let animationId: number | null = null;
  let raycaster: THREE.Raycaster | null = null;
  let mouse: THREE.Vector2 | null = null;
  let currentHighlightedMeshes: Set<string> = new Set();
  let currentHoveredMesh: string | null = null;
  let onMeshHoverCallback: ((meshName: string | null) => void) | null = null;
  let meshNameToMuscleGroup: Map<string, string> = new Map();
  let cachedMeshArray: THREE.Mesh[] = [];
  let needsRaycast = false;
  let pendingMouseX = 0;
  let pendingMouseY = 0;
  let meshNameIndex: string[] = [];

  const applyCameraPose = (position: [number, number, number], target: [number, number, number] = [0, 1, 0]) => {
    if (!camera.value || !controls.value) return;
    camera.value.position.set(position[0], position[1], position[2]);
    controls.value.target.set(target[0], target[1], target[2]);
    controls.value.update();
  };

  const waitForContainerSize = (container: HTMLElement, maxRetries = 20): Promise<{ w: number; h: number }> => {
    return new Promise((resolve) => {
      let attempts = 0;
      const check = () => {
        const w = container.clientWidth;
        const h = container.clientHeight;
        if (w > 0 && h > 0) {
          log('Container size ready:', w, 'x', h);
          resolve({ w, h });
        } else if (attempts < maxRetries) {
          attempts++;
          log(`Container size not ready (attempt ${attempts}/${maxRetries}):`, w, 'x', h);
          requestAnimationFrame(check);
        } else {
          log('Container size fallback: using 800x600');
          resolve({ w: 800, h: 600 });
        }
      };
      check();
    });
  };

  const init = async (container: HTMLElement) => {
    containerEl = container;
    log('init() called, container:', container.tagName, container.className);

    const { w, h } = await waitForContainerSize(container);
    log('Initializing Three.js with size:', w, 'x', h);

    const threeScene = new THREE.Scene();
    threeScene.fog = new THREE.FogExp2(0x1F2937, 0.015);
    scene.value = markRaw(threeScene);

    const threeCamera = new THREE.PerspectiveCamera(45, w / h, 0.1, 100);
    threeCamera.position.set(0, 1.5, 4);
    camera.value = markRaw(threeCamera);

    const threeRenderer = new THREE.WebGLRenderer({ antialias: true, alpha: true });
    threeRenderer.setSize(w, h);
    threeRenderer.setPixelRatio(Math.min(window.devicePixelRatio, 2));
    threeRenderer.toneMapping = THREE.ACESFilmicToneMapping;
    threeRenderer.toneMappingExposure = 1.0;
    container.appendChild(threeRenderer.domElement);
    renderer.value = markRaw(threeRenderer);
    log('Canvas appended to container');

    const ambientLight = new THREE.AmbientLight(0xffffff, 0.6);
    threeScene.add(ambientLight);

    const mainLight = new THREE.DirectionalLight(0xffffff, 1.5);
    mainLight.position.set(5, 10, 7);
    threeScene.add(mainLight);

    const fillLight = new THREE.DirectionalLight(0xe2e8f0, 0.8);
    fillLight.position.set(-5, 3, -5);
    threeScene.add(fillLight);

    const gridHelper = new THREE.GridHelper(10, 20, 0x4fd1c5, 0x374151);
    gridHelper.position.y = -0.01;
    threeScene.add(gridHelper);

    const threeControls = new OrbitControls(threeCamera, threeRenderer.domElement);
    threeControls.enableDamping = true;
    threeControls.dampingFactor = 0.05;
    threeControls.minDistance = 1.8;
    threeControls.maxDistance = 8;
    threeControls.target.set(0, 1, 0);
    controls.value = markRaw(threeControls);

    raycaster = new THREE.Raycaster();
    mouse = new THREE.Vector2();

    threeRenderer.domElement.addEventListener('mousemove', onCanvasMouseMove);
    threeRenderer.domElement.addEventListener('mouseleave', onCanvasMouseLeave);

    resizeObserver = new ResizeObserver(() => {
      if (!containerEl || !camera.value || !renderer.value) return;
      const cw = containerEl.clientWidth;
      const ch = containerEl.clientHeight;
      if (cw === 0 || ch === 0) return;
      camera.value.aspect = cw / ch;
      camera.value.updateProjectionMatrix();
      renderer.value.setSize(cw, ch);
    });
    resizeObserver.observe(container);

    animate();
    log('Three.js init complete');
  };

  const setCameraView = (preset: 'front' | 'back' | 'left' | 'right' | 'focus') => {
    switch (preset) {
      case 'front':
        applyCameraPose([0, 1.45, 4.1]);
        break;
      case 'back':
        applyCameraPose([0, 1.45, -4.1]);
        break;
      case 'left':
        applyCameraPose([4.1, 1.3, 0.15]);
        break;
      case 'right':
        applyCameraPose([-4.1, 1.3, 0.15]);
        break;
      case 'focus':
      default:
        applyCameraPose([2.8, 1.7, 3.2]);
        break;
    }
  };

  const onCanvasMouseMove = (event: MouseEvent) => {
    if (!containerEl || !camera.value || !raycaster || !mouse || !modelLoaded.value) return;

    const rect = containerEl.getBoundingClientRect();
    pendingMouseX = ((event.clientX - rect.left) / rect.width) * 2 - 1;
    pendingMouseY = -((event.clientY - rect.top) / rect.height) * 2 + 1;
    needsRaycast = true;
  };

  const processRaycast = () => {
    if (!raycaster || !mouse || !camera.value || !modelLoaded.value) return;
    if (!needsRaycast) return;
    needsRaycast = false;

    mouse.x = pendingMouseX;
    mouse.y = pendingMouseY;

    raycaster.setFromCamera(mouse, camera.value);

    const meshArray = cachedMeshArray.length > 0 ? cachedMeshArray : Array.from(muscleMeshes.value.values());
    const intersects = raycaster.intersectObjects(meshArray, false);

    if (intersects.length > 0) {
      const hitMesh = intersects[0].object as THREE.Mesh;
      const hitName = hitMesh.name;
      if (hitName && currentHoveredMesh !== hitName) {
        currentHoveredMesh = hitName;
        hoveredMeshName.value = hitName;
        if (onMeshHoverCallback) {
          onMeshHoverCallback(hitName);
        }
      }
    } else {
      if (currentHoveredMesh !== null) {
        currentHoveredMesh = null;
        hoveredMeshName.value = null;
        if (onMeshHoverCallback) {
          onMeshHoverCallback(null);
        }
      }
    }
  };

  const onCanvasMouseLeave = () => {
    needsRaycast = false;
    if (currentHoveredMesh !== null) {
      currentHoveredMesh = null;
      hoveredMeshName.value = null;
      if (onMeshHoverCallback) {
        onMeshHoverCallback(null);
      }
    }
  };

  const setOnMeshHover = (callback: (meshName: string | null) => void) => {
    onMeshHoverCallback = callback;
  };

  const registerMuscleGroupMapping = (mapping: Map<string, string>) => {
    meshNameToMuscleGroup = mapping;
  };

  const getMuscleGroupIdByMesh = (meshName: string): string | null => {
    return meshNameToMuscleGroup.get(meshName) || null;
  };

  const animate = () => {
    animationId = requestAnimationFrame(animate);
    const ctrl = controls.value;
    if (ctrl) ctrl.update();
    processRaycast();
    const r = renderer.value;
    const s = scene.value;
    const c = camera.value;
    if (r && s && c) {
      r.render(s, c);
    }
  };

  const isExcluded = (lowerName: string): boolean => {
    return EXCLUDE_PATTERNS.some(p => lowerName.includes(p));
  };

  const matchKeywordToMesh = (keyword: string, lowerMeshName: string): boolean => {
    if (lowerMeshName.includes(keyword)) return true;

    const normMesh = normalizeMeshName(lowerMeshName);
    if (normMesh.includes(keyword)) return true;

    const meshTokens = tokenize(lowerMeshName);
    const keywordTokens = tokenize(keyword);
    if (keywordTokens.length > 1 && meshTokens.length > 1) {
      const overlap = keywordTokens.filter(kt => meshTokens.some(mt => mt === kt || mt.includes(kt) || kt.includes(mt)));
      if (overlap.length >= Math.ceil(keywordTokens.length * 0.7)) return true;
    }

    return false;
  };

  const findMatchingMeshes = (keywords: string[]): { matched: Map<string, string[]>, unmatched: string[] } => {
    const lowerKeywords = keywords.map(k => k.toLowerCase());
    const matched = new Map<string, string[]>();
    const unmatched: string[] = [];

    lowerKeywords.forEach(keyword => {
      const hits: string[] = [];
      muscleMeshes.value.forEach((_, meshName) => {
        const lowerMeshName = meshName.toLowerCase();
        if (isExcluded(lowerMeshName)) return;
        if (!isMuscleMesh(lowerMeshName)) return;
        if (matchKeywordToMesh(keyword, lowerMeshName)) {
          hits.push(meshName);
        }
      });

      if (hits.length > 0) {
        matched.set(keyword, hits);
      } else {
        unmatched.push(keyword);
      }
    });

    return { matched, unmatched };
  };

  const verifyKeywords = (keywords: string[]): { verified: number; unmatched: string[] } => {
    const { matched, unmatched } = findMatchingMeshes(keywords);
    if (unmatched.length > 0) {
      console.warn('[useThree] Keyword verification - unmatched keywords:', unmatched);
      unmatched.forEach(kw => {
        console.warn(`[useThree]   "${kw}" - no matching mesh found`);
      });
    }
    log(`[useThree] Keyword verification: ${matched.size}/${keywords.length} keywords matched, ${unmatched.length} unmatched`);
    return { verified: matched.size, unmatched };
  };

  const loadModel = async (url: string): Promise<string[]> => {
    log('loadModel() called with URL:', url);

    return new Promise((resolve, reject) => {
      const s = scene.value;
      if (!s) {
        const err = 'Three.js scene not initialized - call init() first';
        log('ERROR:', err);
        loadError.value = err;
        reject(new Error(err));
        return;
      }

      loading.value = true;
      loadProgress.value = 0;
      loadError.value = null;

      if (currentModel.value) {
        s.remove(currentModel.value);
        muscleMeshes.value.clear();
      }

      const loader = new GLTFLoader();

      try {
        const dracoLoader = new DRACOLoader();
        dracoLoader.setDecoderPath('https://www.gstatic.com/draco/versioned/decoders/1.5.6/');
        loader.setDRACOLoader(dracoLoader);
        log('DRACOLoader configured');
      } catch (e) {
        log('DRACOLoader init failed, falling back:', e);
      }

      log('Starting GLTFLoader.load()...');

      loader.load(
        url,
        (gltf) => {
          log('GLTF loaded successfully, processing model...');
          try {
            const model = gltf.scene;
            const detectedMeshes: string[] = [];
            const muscleMeshNames: string[] = [];

            const box = new THREE.Box3().setFromObject(model);
            const center = box.getCenter(new THREE.Vector3());
            const size = box.getSize(new THREE.Vector3());
            const maxDim = Math.max(size.x, size.y, size.z);
            const scale = 2.5 / maxDim;

            model.scale.set(scale, scale, scale);
            model.position.sub(center.multiplyScalar(scale));
            model.position.y += (size.y * scale) / 2;

            model.traverse((child) => {
              if (child instanceof THREE.Mesh) {
                const mesh = child;
                detectedMeshes.push(mesh.name);

                cloneMeshMaterials(mesh);

                const mat = getColorEditableMaterials(mesh)[0];
                if (mat) {
                  mesh.userData.originalColor = mat.color ? mat.color.clone() : new THREE.Color(0xffffff);
                  mesh.userData.originalEmissive = mat.emissive ? mat.emissive.clone() : new THREE.Color(0x000000);
                  mesh.userData.isHighlighted = false;
                  mesh.userData.isHovered = false;
                }

                muscleMeshes.value.set(mesh.name, markRaw(mesh));

                const lowerName = mesh.name.toLowerCase();
                if (!isExcluded(lowerName) && isMuscleMesh(lowerName)) {
                  muscleMeshNames.push(mesh.name);
                }
              }
            });

            currentModel.value = markRaw(model);
            s.add(model);
            modelLoaded.value = true;
            loading.value = false;
            loadProgress.value = 100;
            cachedMeshArray = Array.from(muscleMeshes.value.values());
            meshNameIndex = muscleMeshNames.sort();

            log('Model loaded! Total meshes:', detectedMeshes.length, '| Muscle meshes:', muscleMeshNames.length);
            log('Muscle mesh names sample:', muscleMeshNames.slice(0, 15));

            resolve(detectedMeshes);
          } catch (traverseError) {
            loading.value = false;
            const msg = traverseError instanceof Error ? traverseError.message : String(traverseError);
            loadError.value = `模型解析失败: ${msg}`;
            log('Model traverse error:', msg);
            reject(traverseError);
          }
        },
        (xhr) => {
          if (xhr.lengthComputable) {
            loadProgress.value = Math.round((xhr.loaded / xhr.total) * 100);
            log(`Loading progress: ${loadProgress.value}% (${xhr.loaded}/${xhr.total})`);
          } else {
            log(`Loading progress: ${xhr.loaded} bytes loaded (total unknown)`);
          }
        },
        (error) => {
          loading.value = false;
          const msg = error instanceof Error ? error.message : String(error);
          loadError.value = `模型加载失败: ${msg}`;
          log('GLTFLoader error:', msg, error);
          reject(error);
        }
      );
    });
  };

  const resetMuscles = () => {
    muscleMeshes.value.forEach((mesh) => {
      const materials = getColorEditableMaterials(mesh);
      materials.forEach((mat) => {
        if (mat.color && mesh.userData.originalColor) mat.color.copy(mesh.userData.originalColor);
        if (mat.emissive && mesh.userData.originalEmissive) mat.emissive.copy(mesh.userData.originalEmissive);
      });
      mesh.userData.isHighlighted = false;
      mesh.userData.isHovered = false;
    });
    currentHighlightedMeshes.clear();
  };

  const highlightMuscles = (keywordsArray: string[]): string[] => {
    resetMuscles();
    const unmatchedKeywords = new Set(keywordsArray);

    muscleMeshes.value.forEach((mesh, meshName) => {
      const lowerMeshName = meshName.toLowerCase();
      if (isExcluded(lowerMeshName)) return;
      if (!isMuscleMesh(lowerMeshName)) return;

      let isMatch = false;

      keywordsArray.forEach((keyword) => {
        if (matchKeywordToMesh(keyword.toLowerCase(), lowerMeshName)) {
          isMatch = true;
          unmatchedKeywords.delete(keyword);
        }
      });

      if (isMatch) {
        currentHighlightedMeshes.add(meshName);
        mesh.userData.isHighlighted = true;
        const materials = getColorEditableMaterials(mesh);
        materials.forEach((mat) => {
          if (mat.color) mat.color.setHex(HIGHLIGHT_COLOR);
          if (mat.emissive) mat.emissive.setHex(EMISSIVE_COLOR);
        });
      }
    });

    if (unmatchedKeywords.size > 0) {
      console.warn('[useThree] highlightMuscles - unmatched keywords:', Array.from(unmatchedKeywords));
    }

    return Array.from(unmatchedKeywords);
  };

  const highlightMeshesByKeywords = (keywords: string[]): string[] => {
    resetMuscles();
    const lowerKeywords = keywords.map(k => k.toLowerCase());
    const unmatchedKeywords = new Set(lowerKeywords);
    const matchDetails = new Map<string, string[]>();

    muscleMeshes.value.forEach((mesh, meshName) => {
      const lowerMeshName = meshName.toLowerCase();
      if (isExcluded(lowerMeshName)) return;
      if (!isMuscleMesh(lowerMeshName)) return;

      let isMatch = false;
      const matchedBy: string[] = [];

      lowerKeywords.forEach((keyword) => {
        if (matchKeywordToMesh(keyword, lowerMeshName)) {
          isMatch = true;
          unmatchedKeywords.delete(keyword);
          matchedBy.push(keyword);
        }
      });

      if (isMatch) {
        currentHighlightedMeshes.add(meshName);
        mesh.userData.isHighlighted = true;
        const materials = getColorEditableMaterials(mesh);
        materials.forEach((mat) => {
          if (mat.color) mat.color.setHex(HIGHLIGHT_COLOR);
          if (mat.emissive) mat.emissive.setHex(EMISSIVE_COLOR);
        });
        matchedBy.forEach(kw => {
          const existing = matchDetails.get(kw) || [];
          existing.push(meshName);
          matchDetails.set(kw, existing);
        });
      }
    });

    const trulyUnmatched = Array.from(unmatchedKeywords).filter(kw => !KNOWN_MISSING_MESHES.has(kw));
    const knownMissing = Array.from(unmatchedKeywords).filter(kw => KNOWN_MISSING_MESHES.has(kw));
    const matchedKeywordList = lowerKeywords.filter(kw => !unmatchedKeywords.has(kw));

    const logEntry: MatchLogEntry = {
      timestamp: Date.now(),
      keywords: lowerKeywords,
      matchedKeywords: matchedKeywordList,
      unmatchedKeywords: Array.from(unmatchedKeywords),
      matchDetails,
    };
    matchLog.value = [...matchLog.value, logEntry];

    if (unmatchedKeywords.size > 0) {
      log('=== Match Report ===');
      log(`Total keywords: ${lowerKeywords.length}, Matched: ${matchedKeywordList.length}, Unmatched: ${unmatchedKeywords.size}`);
      matchDetails.forEach((meshes, kw) => {
        log(`  ✓ "${kw}" → [${meshes.join(', ')}]`);
      });
      if (trulyUnmatched.length > 0) {
        console.warn('[useThree] Unexpected unmatched keywords:', trulyUnmatched);
        trulyUnmatched.forEach(kw => {
          console.warn(`[useThree]   "${kw}" - no matching mesh found`);
          log(`  ✗ "${kw}" - no matching mesh (unexpected)`);
        });
      }
      if (knownMissing.length > 0) {
        log(`  ⓘ Known missing meshes (expected): ${knownMissing.join(', ')}`);
      }
      log('Available muscle meshes sample:', meshNameIndex.slice(0, 30));
    } else {
      log(`highlightMeshesByKeywords - all ${lowerKeywords.length} keywords matched successfully`);
    }

    return trulyUnmatched;
  };

  const hoverMeshesByKeywords = (keywords: string[]) => {
    const lowerKeywords = keywords.map(k => k.toLowerCase());

    muscleMeshes.value.forEach((mesh, mName) => {
      const lowerMeshName = mName.toLowerCase();
      const shouldHover = !isExcluded(lowerMeshName) && isMuscleMesh(lowerMeshName) && lowerKeywords.some(kw => matchKeywordToMesh(kw, lowerMeshName));
      const materials = getColorEditableMaterials(mesh);

      if (mesh.userData.isHighlighted) {
        if (shouldHover) {
          materials.forEach((mat) => {
            if (mat.emissive) mat.emissive.setHex(0x882200);
          });
        } else {
          materials.forEach((mat) => {
            if (mat.emissive) mat.emissive.setHex(EMISSIVE_COLOR);
          });
        }
        return;
      }

      if (mesh.userData.isHovered && !shouldHover) {
        mesh.userData.isHovered = false;
        materials.forEach((mat) => {
          if (mat.color && mesh.userData.originalColor) mat.color.copy(mesh.userData.originalColor);
          if (mat.emissive && mesh.userData.originalEmissive) mat.emissive.copy(mesh.userData.originalEmissive);
        });
      } else if (!mesh.userData.isHovered && shouldHover) {
        mesh.userData.isHovered = true;
        materials.forEach((mat) => {
          if (mat.color) mat.color.setHex(HOVER_COLOR);
          if (mat.emissive) mat.emissive.setHex(HOVER_EMISSIVE);
        });
      }
    });
  };

  const highlightSingleMuscle = (meshName: string) => {
    resetMuscles();
    const mesh = muscleMeshes.value.get(meshName);
    if (mesh) {
      const materials = getColorEditableMaterials(mesh);
      materials.forEach((mat) => {
        if (mat.color) mat.color.setHex(HIGHLIGHT_COLOR);
        if (mat.emissive) mat.emissive.setHex(EMISSIVE_COLOR);
      });
    }
  };

  const retryLoad = async (url: string): Promise<string[]> => {
    loadError.value = null;
    return loadModel(url);
  };

  const dispose = () => {
    if (animationId !== null) {
      cancelAnimationFrame(animationId);
      animationId = null;
    }
    if (resizeObserver && containerEl) {
      resizeObserver.unobserve(containerEl);
      resizeObserver.disconnect();
      resizeObserver = null;
    }
    if (containerEl && renderer.value) {
      renderer.value.domElement.removeEventListener('mousemove', onCanvasMouseMove);
      renderer.value.domElement.removeEventListener('mouseleave', onCanvasMouseLeave);
    }
    const ctrl = controls.value;
    if (ctrl) ctrl.dispose();
    const r = renderer.value;
    if (r) {
      r.dispose();
      if (containerEl && r.domElement.parentNode === containerEl) {
        containerEl.removeChild(r.domElement);
      }
    }
    if (currentModel.value && scene.value) {
      scene.value.remove(currentModel.value);
    }
    containerEl = null;
  };

  const getMatchLog = () => matchLog.value;
  const clearMatchLog = () => { matchLog.value = []; };

  onUnmounted(() => {
    dispose();
  });

  return {
    scene,
    camera,
    renderer,
    modelLoaded,
    loading,
    loadProgress,
    loadError,
    muscleMeshes,
    hoveredMeshName,
    matchLog,
    init,
    loadModel,
    retryLoad,
    setCameraView,
    resetMuscles,
    highlightMuscles,
    highlightMeshesByKeywords,
    hoverMeshesByKeywords,
    highlightSingleMuscle,
    setOnMeshHover,
    registerMuscleGroupMapping,
    getMuscleGroupIdByMesh,
    verifyKeywords,
    getMatchLog,
    clearMatchLog,
    dispose,
  };
}
