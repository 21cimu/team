const fs = require('fs');
const buf = fs.readFileSync('public/assets/body.glb');

const chunk0Length = buf.readUInt32LE(12);
const chunk0Type = buf.readUInt32LE(16);

if (chunk0Type === 0x4E4F534A) {
  const jsonStr = buf.toString('utf8', 20, 20 + chunk0Length);
  const gltf = JSON.parse(jsonStr);
  
  const namedMeshes = [];
  if (gltf.meshes) {
    gltf.meshes.forEach((mesh) => {
      if (mesh.name && mesh.name !== 'Mesh') {
        namedMeshes.push(mesh.name);
      }
    });
  }
  
  namedMeshes.sort();
  
  // Filter only muscle-related meshes (exclude bones, teeth, etc.)
  const muscleKeywords = ['muscle', 'head of', 'part of', 'tendon', 'biceps', 'triceps', 'deltoid', 'gluteus', 'pectoralis', 'latissimus', 'trapezius', 'quadriceps', 'hamstring', 'serratus', 'oblique', 'abdominis', 'erector', 'soleus', 'gastrocnemius', 'tibialis', 'brachialis', 'supinator', 'pronator', 'flexor', 'extensor', 'adductor', 'abductor', 'rhomboid', 'masseter', 'scalene', 'splenius', 'spinalis', 'semispinalis', 'iliocostalis', 'longissimus', 'multifidus', 'rotatores', 'intercostal', 'diaphragm', 'levator', 'coccygeus', 'piriformis', 'gemellus', 'obturator', 'popliteus', 'plantaris', 'peroneus', 'brevis', 'longus', 'major', 'minor', 'rectus', 'vastus', 'gracilis', 'sartorius', 'tensor', 'coracobrachialis', 'digastric', 'mylohyoid', 'geniohyoid', 'stylohyoid', 'thyrohyoid', 'sternohyoid', 'omohyoid', 'cricothyroid', 'arytenoid', 'constrictor', 'palatoglossus', 'palatopharyngeus', 'salpingopharyngeus', 'stylopharyngeus'];
  
  const muscleMeshes = namedMeshes.filter(name => {
    const lower = name.toLowerCase();
    return muscleKeywords.some(kw => lower.includes(kw));
  });
  
  // Remove .001 duplicates (mirror side)
  const uniqueMuscles = [...new Set(muscleMeshes.map(n => n.replace(/\.001$/, '')))].sort();
  
  console.log('=== MUSCLE MESHES (' + uniqueMuscles.length + ' unique) ===');
  uniqueMuscles.forEach(n => console.log(n));
}
